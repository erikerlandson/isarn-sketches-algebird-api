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

import scala.language.implicitConversions

import com.twitter.algebird.Monoid

import org.isarnproject.sketches.TDigest

/**
 * Implicit definitions for Algebird objects based on TDigest
 */
object implicits {
  private val tDigestMonoid = AlgebirdFactory.tDigestMonoid

  /**
   * Implicit definition of TDigest as an Algebird Monoid
   * @note the implicit Monoid[TDigest] uses default sketch resolution parameter delta.
   * See AlgebirdFactory for non-default delta values
   */
  implicit def tDigestMonoidImplicit: Monoid[TDigest] = tDigestMonoid
}
