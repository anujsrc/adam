/*
 * Copyright (c) 2014. Regents of the University of California
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

package org.bdgenomics.adam.util

import scala.annotation.tailrec
import net.sf.samtools.{ Cigar, CigarOperator, CigarElement }
import org.bdgenomics.adam.avro.ADAMRecord
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rich.RichADAMRecord
import org.bdgenomics.adam.rich.RichADAMRecord._
import org.bdgenomics.adam.rich.RichCigar
import org.bdgenomics.adam.rich.RichCigar._

object NormalizationUtils {

  /**
   * Given a cigar, returns the cigar with the position of the cigar shifted left.
   *
   * @param cigar Cigar to left align.
   * @return Cigar fully moved left.
   */
  def leftAlignIndel(read: ADAMRecord): Cigar = {
    var indelPos = -1
    var pos = 0
    var indelLength = 0
    var readPos = 0
    var referencePos = 0
    var isInsert = false
    val richRead = RichADAMRecord(read)
    val cigar = richRead.samtoolsCigar

    val clippedOffset = if (cigar.getCigarElements.head.getOperator == CigarOperator.SOFT_CLIP) {
      cigar.getCigarElements.head.getLength
    } else {
      0
    }

    // find indel in cigar
    cigar.getCigarElements.map(elem => {
      elem.getOperator match {
        case (CigarOperator.I) => {
          if (indelPos == -1) {
            indelPos = pos
            indelLength = elem.getLength
          } else {
            // if we see a second indel, return the cigar
            return cigar
          }
          pos += 1
          isInsert = true
        }
        case (CigarOperator.D) => {
          if (indelPos == -1) {
            indelPos = pos
            indelLength = elem.getLength
          } else {
            // if we see a second indel, return the cigar
            return cigar
          }
          pos += 1
        }
        case _ => {
          pos += 1
          if (indelPos == -1) {
            if (elem.getOperator.consumesReadBases()) {
              readPos += elem.getLength
            }
            if (elem.getOperator.consumesReferenceBases()) {
              referencePos += elem.getLength
            }
          }
        }
      }
    })

    // if there is an indel, shift it, else return
    if (indelPos != -1) {

      val readSeq: String = read.getSequence()

      // if an insert, get variant and preceeding bases from read
      // if delete, pick variant, from reference, preceeding bases from read
      val variant = if (isInsert) {
        readSeq.drop(readPos).take(indelLength)
      } else {
        val refSeq = richRead.mdTag.get.getReference(read)
        refSeq.drop(referencePos).take(indelLength)
      }

      // preceeding sequence must always come from read
      // if preceeding sequence does not come from read, we may left shift through a SNP
      val preceeding = readSeq.take(readPos)

      // identify the number of bases to shift by
      val shiftLength = numberOfPositionsToShiftIndel(variant, preceeding)

      shiftIndel(cigar, indelPos, shiftLength)
    } else {
      cigar
    }
  }

  /**
   * Returns the maximum number of bases that an indel can be shifted left during left normalization.
   * Requires that the indel has been trimmed. For an insertion, this should be called on read data
   *
   * @param variant Bases of indel variant sequence.
   * @param preceeding Bases of sequence to left of variant.
   * @return The number of bases to shift an indel for it to be left normalized.
   */
  def numberOfPositionsToShiftIndel(variant: String, preceeding: String): Int = {

    // tail recursive function to determine shift
    @tailrec def numberOfPositionsToShiftIndelAccumulate(variant: String, preceeding: String, accumulator: Int): Int = {
      if (preceeding.length == 0 || preceeding.last != variant.last) {
        // the indel cannot be moved further left if we do not have bases in front of our indel, or if we cannot barrel rotate the indel
        accumulator
      } else {
        // barrel rotate variant
        val newVariant = variant.last + variant.dropRight(1)
        // trim preceeding sequence
        val newPreceeding = preceeding.dropRight(1)
        numberOfPositionsToShiftIndelAccumulate(newVariant, newPreceeding, accumulator + 1)
      }
    }

    numberOfPositionsToShiftIndelAccumulate(variant, preceeding, 0)
  }

  /**
   * Shifts an indel left by n. Is tail call recursive.
   *
   * @param cigar Cigar to shift.
   * @param position Position of element to move.
   * @param shifts Number of bases to shift element.
   * @return Cigar that has been shifted as far left as possible.
   */
  @tailrec def shiftIndel(cigar: Cigar, position: Int, shifts: Int): Cigar = {
    // generate new cigar with indel shifted by one
    val newCigar = new Cigar(cigar.getCigarElements).moveLeft(position)

    // if there are no more shifts to do, or if shifting breaks the cigar, return old cigar
    if (shifts == 0 || !newCigar.isWellFormed(cigar.getLength)) {
      cigar
    } else {
      shiftIndel(newCigar, position, shifts - 1)
    }
  }
}
