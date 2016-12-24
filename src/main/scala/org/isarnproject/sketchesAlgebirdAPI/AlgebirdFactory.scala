/*
Copyright 2016 Erik Erlandson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.isarnproject.sketchesAlgebirdAPI

import com.twitter.algebird.{ Monoid, Aggregator, MonoidAggregator }

import org.isarnproject.sketches.TDigest

/**
 * Factory functions for generating Algebird objects based on TDigest
 */
object AlgebirdFactory {
  /**
   * Obtain a new Monoid type-class object based on TDigest
   * @return A new Monoid with respect to a TDigest with default sketch resolution
   */
  def tDigestMonoid: Monoid[TDigest] = tDigestMonoid(TDigest.deltaDefault)

  /**
   * Obtain a new Monoid type-class object based on TDigest
   * @param delta The TDigest sketch resolution parameter
   * @return A new Monoid with respect to a TDigest with the given delta
   */
  def tDigestMonoid(delta: Double): Monoid[TDigest] =
    new Monoid[TDigest] {
      def zero = TDigest.empty(delta)
      def plus(x: TDigest, y: TDigest): TDigest = x ++ y      
    }

  /**
   * Obtain an Aggregator for sketching data of a given numeric type, using a TDigest sketch
   * @tparam N the numeric type to be aggregated
   * @return An Aggregator that sketches data of type N using TDigest, with default sketch
   * resolution parameter delta
   */
  def tDigestAggregator[N](implicit num: Numeric[N]): MonoidAggregator[N, TDigest, TDigest] =
    tDigestAggregator[N](TDigest.deltaDefault)

  /**
   * Obtain an Aggregator for sketching data of a given numeric type, using a TDigest sketch
   * @tparam N the numeric type to be aggregated
   * @param delta the TDigest sketch resolution parameter
   * @return An Aggregator that sketches data of type N using TDigest, with the given sketch
   * resolution parameter delta
   */
  def tDigestAggregator[N](delta: Double)(implicit num: Numeric[N]):
      MonoidAggregator[N, TDigest, TDigest]
    = Aggregator.appendMonoid((t: TDigest, x: N) => t + x)(tDigestMonoid(delta))
}

object experiment {
  import java.io._

  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._

  import org.apache.commons.math3.distribution._

  import org.isarnproject.sketches.tdmap.TDigestMap

  def combine(ltd: TDigest, rtd: TDigest, delta: Double = TDigest.deltaDefault): TDigest = {
    if (ltd.nclusters <= 1 && rtd.nclusters > 1) combine(rtd, ltd, delta)
    else if (rtd.nclusters == 0) ltd
    else if (rtd.nclusters == 1) {
      // handle the singleton RHS case specially to prevent quadratic catastrophe when
      // it is being used in the Aggregator use case
      val d = rtd.clusters.asInstanceOf[org.isarnproject.sketches.tdmap.tree.INodeTD].data
      ltd + ((d.key, d.value))
    } else {
      // insert clusters from largest to smallest, instead of randomly
      (ltd.clusters.toVector ++ rtd.clusters.toVector).sortWith((a, b) => a._2 > b._2)
        .foldLeft(TDigest.empty(delta))((d, e) => d + e)
    }
  }

  def monoid: Monoid[TDigest] =
    new Monoid[TDigest] {
      def zero = TDigest.empty(TDigest.deltaDefault)
      def plus(x: TDigest, y: TDigest): TDigest = combine(x, y, x.delta)
    }

  def q2k(q: Double) = 0.5 + math.asin(2.0 * math.min(1.0, q) - 1.0) / math.Pi

  def combineMerge(ltd: TDigest, rtd: TDigest, delta: Double = TDigest.deltaDefault): TDigest = {
    if (ltd.nclusters <= 1 && rtd.nclusters > 1) combine(rtd, ltd, delta)
    else if (rtd.nclusters == 0) ltd
    else if (rtd.nclusters == 1) {
      // handle the singleton RHS case specially to prevent quadratic catastrophe when
      // it is being used in the Aggregator use case
      val d = rtd.clusters.asInstanceOf[org.isarnproject.sketches.tdmap.tree.INodeTD].data
      ltd + ((d.key, d.value))
    } else {
      // combine using Ted Dunning's latest merge-based algorithm
      // sort clusters by their centroids, merge any clusters with same center
      val clust = (ltd.clusters.toVector ++ rtd.clusters.toVector)
        .sortWith((a, b) => a._1 < b._1)
        .foldLeft(Vector.empty[(Double, Double)]) { case (v, (x, w)) =>
          if (v.length > 0 && x == v.last._1) v.dropRight(1) :+ (x, v.last._2 + w)
          else v :+ (x, w)
        }
      val R = TDigest.K / delta
      val z = clust.iterator.map { case (_, w) => w }.sum
      var q = 0.0
      var (xc, wc) = clust.head
      val merged = scala.collection.mutable.ArrayBuffer.empty[(Double, Double)]
      for ((x, w) <- clust.tail) {
        val dq = (wc + w) / z
        val kTest = R * (q2k(q + dq) - q2k(q))
        if (kTest > 1.0) {
          merged += ((xc, wc))
          q += wc / z
          xc = x
          wc = w
        } else {
          xc += w * (x - xc) / (wc + w)
          wc += w
        }
      }
      merged += ((xc, wc))
      val tdMap = merged.foldLeft(TDigestMap.empty) { case (m, e) => m + e }
      TDigest(delta, tdMap.size, tdMap)
    }
  }

  // Kolmogorov-Smirnov D statistic
  def ksD(td: TDigest, dist: RealDistribution): Double = {
    val xmin = td.clusters.keyMin.get
    val xmax = td.clusters.keyMax.get
    val step = (xmax - xmin) / 1000.0
    val d = (xmin to xmax by step).iterator
      .map(x => math.abs(td.cdf(x) - dist.cumulativeProbability(x))).max
    d
  }

  def collect(mon: Monoid[TDigest], dist: RealDistribution) = {
    val sampleSize = 10
    val dataSize = 10000
    val nsums = 100
    val sumSample = 10
    val raw = Vector.fill(sampleSize) {
      val data = Vector.fill(1 + nsums) { Vector.fill(dataSize) { dist.sample } }
      // monoidal addition as defined in the original paper
      val ref = data.map(TDigest.sketch(_)).scanLeft(TDigest.empty())(TDigest.combine(_, _)).drop(1).map(ksD(_, dist))
      // addition using newer merge-based algorithm
      val ref2 = data.map(TDigest.sketch(_)).scanLeft(TDigest.empty())(combineMerge(_, _)).drop(1).map(ksD(_, dist))
      // experimental definition where clusters are inserted from largest to smallest
      val exp = data.map(TDigest.sketch(_)).scanLeft(TDigest.empty())(combine(_, _)).drop(1).map(ksD(_, dist))
      (ref, exp, ref2)
    }
    val step = math.max(1, nsums / sumSample)
    val jvals = 0 to nsums by step
    val ref = jvals.flatMap(j => raw.map(_._1(j)))
    val ref2 = jvals.flatMap(j => raw.map(_._3(j)))
    val exp = jvals.flatMap(j => raw.map(_._2(j)))
    val jvf = jvals.flatMap(j => Vector.fill(sampleSize)(j))
    (ref, ref2, exp, jvf)
  }

  def writeJSON(data: Seq[(Seq[Double], Seq[Double], Seq[Double], Seq[Int])], fname: String) {
    val json = data.map { case (ref, ref2, exp, jv) =>
      ("ref" -> ref) ~ ("ref2" -> ref2) ~ ("exp" -> exp) ~ ("jv" -> jv)
    }
    val out = new PrintWriter(new File(fname))
    out.println(pretty(render(json)))
    out.close()
  }

  def run(fname: String) {
    writeJSON(
      Vector(
        collect(monoid, new NormalDistribution()),
        collect(monoid, new UniformRealDistribution()),
        collect(monoid, new ExponentialDistribution(1.0))
      ),
      fname)
  }
}
