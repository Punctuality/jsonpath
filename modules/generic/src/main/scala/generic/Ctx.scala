/*
 * Copyright 2023 Filippo De Luca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.filippodeluca.jsonpath.generic

import cats.syntax.all.*
import com.filippodeluca.jsonpath.ast.*

abstract class Ctx[T <: Ctx[T, A], A] {
  def root: A
  def value: Option[A]
  def values: Vector[A]

  def one(value: A, root: A): T
  def many(values: Vector[A], root: A): T

  def loop(exp: Exp): T

  def sequenceValue(a: A): Option[Seq[A]]
  def sequenceToValue(seq: Seq[A]): A

  def mapValue(a: A): Option[Map[String, A]]

  def intValue(a: A): Option[Int]
  def stringValue(a: A): Option[String]

  def sliceSequence(slice: ArraySlice): T = {
    val targetCtx = loop(slice.target)
    val results = targetCtx.values.map { target =>
      val seq = sequenceValue(target)
      seq.fold(sequenceToValue(Vector.empty[A])) { seq =>
        val targetCtx = one(target, root)
        val start = targetCtx.loop(slice.start).value.flatMap(intValue)
        val end = targetCtx.loop(slice.end).value.flatMap(intValue)
        val step = targetCtx.loop(slice.step).value.flatMap(intValue).getOrElse(1)

        val range = if (step > 0) {
          start.map(x => if (x < 0) seq.size + x else x).getOrElse(0) until end
            .map(x => if (x < 0) seq.size + x else x)
            .getOrElse(
              seq.length
            ) by step
        } else {
          (start.map(x => if (x < 0) seq.size + x else x).getOrElse(seq.size)) until end
            .map(x => if (x < 0) seq.size + x else x)
            .getOrElse(-1) by step
        }

        Console.err.println(
          s"start: ${start}, end: ${end}, step: ${step}, range: ${range.toVector}"
        )

        sequenceToValue(
          range.toVector
            .mapFilter { idx =>
              seq.get(idx.toLong)
            }
        )
      }
    }

    many(results, root)
  }

  def getProperty(prop: Property) = {
    val targetCtx = loop(prop.target)
    val results = targetCtx.values.mapFilter { target =>
      // TODO Should id fail if the value is an array?
      val newTargetCtx = one(target, root)
      val name = newTargetCtx.loop(prop.name).value.flatMap(stringValue)
      name
        .flatMap { name =>
          mapValue(target).flatMap(obj => obj.get(name))
        }
    }
    many(results, root)
  }

  def getArrayIndex(idx: ArrayIndex) = {
    val targetCtx = loop(idx.target)
    val results = targetCtx.values.mapFilter { target =>
      // TODO Should id fail if the value is an array?
      val newTargetCtx = one(target, root)
      val index = newTargetCtx.loop(idx.index).value.flatMap(intValue)
      index
        .flatMap { index =>
          sequenceValue(target)
            .flatMap { arr =>
              if (index < 0) {
                arr.get((arr.length + index).toLong)
              } else {
                arr.get(index.toLong)
              }
            }
        }

    }
    many(results, root)
  }

}
