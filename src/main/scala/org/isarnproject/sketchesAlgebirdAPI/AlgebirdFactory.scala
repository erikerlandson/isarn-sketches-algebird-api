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
