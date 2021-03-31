/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.utils

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.agentclientmandate.models.Status
import uk.gov.hmrc.domain.{AtedUtr, Generator}

object Generators {

  //ALL GENERATORS BASED ON TEST DATA - Not guaranteed to look like real specification if there is one

  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  val atedUtr2: AtedUtr = new Generator().nextAtedUtr

  val upperStrGen: Int => Gen[String] = (n: Int) => Gen.listOfN(n, Gen.alphaUpperChar).map(_.mkString)
  val lowerStrGen: Int => Gen[String] = (n: Int) => Gen.listOfN(n, Gen.alphaLowerChar).map(_.mkString)

  val digitGen:Gen[Int] = Gen.choose(0, 9)
  val digitString: Int => Gen[String] = (n:Int) => Gen.listOfN(n, digitGen).map(_.mkString)

  val partyIDGen: Gen[String] =
    for{
      prefix <- upperStrGen(4)
      number <- digitString(4)
    } yield s"$prefix$number"

  val clientIdGen: Gen[String] =
    for{
      prefix <- Gen.const("ATED")
      char <-   Gen.const("-")
      number <- digitString(6)
    } yield s"$prefix$char$number"

  val telephoneNumberGen:Gen[String] = digitString(10)

  val sapNumberGen:Gen[String] = digitString(10)

  val safeIDGen:Gen[String] =
    for{
    prefix <- upperStrGen(2)
    number <- digitString(13)
  } yield s"$prefix$number"

  val emailGen:Gen[String] = for{
    name <- lowerStrGen(4)
    at <- Gen.const("@")
    domain <- lowerStrGen(4)
    dotCom <- Gen.const(".com")
  } yield s"$name$at$domain$dotCom"

  val companyNameGen:Gen[String] = Gen.alphaStr

  val agentReferenceNumberGen:Gen[String] =
    for{
      prefix <- upperStrGen(4)
      number <- digitString(7)
    } yield s"$prefix$number"

  val agentCodeGen:Gen[String] = for {
    prefix <- Gen.const("AGENT-")
    number <- digitString(3)
  } yield s"$prefix$number"


  val agentBusinessUtrGen:Gen[String] =
    for{
      prefix <- upperStrGen(4)
      number <- digitString(7)
    } yield s"$prefix$number"

  val firstNameGen:Gen[String] = Gen.alphaStr
  val lastNameGen:Gen[String] = Gen.alphaStr

  val nameGen:Gen[String] =
    for{
      fn <- firstNameGen
      ln <- lastNameGen
    } yield s"$fn $ln"

  val subscriptionReferenceGen: Gen[String] =
    for {
      prefix <- upperStrGen(1)
      number <- digitString(8)
    } yield s"$prefix$number"

  val mandateReferenceGen: Gen[String] =
    for {
      prefix <- upperStrGen(1)
      number <- digitString(7)
    } yield s"$prefix$number"

  val newEnrolmentGen:Gen[String] = digitString(14)



  private val yearMonthDayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                                                       .withZone(ZoneId.systemDefault())
  val dateOfBirthGen: Gen[String] = Gen.calendar.map(calendar => yearMonthDayFormatter.format(calendar.toInstant))

  val postcodeGen:Gen[String] =
    for{
      startLetters <- upperStrGen(2)
      postfix <- digitString(1)
      place <- digitString(1)
      finalLetters <- upperStrGen(2)
    } yield s"$startLetters$postfix $place$finalLetters"

  implicit val statusGen: Gen[Status.Status] = Gen.oneOf(Status.values.toSeq)
  implicit val statusArbitrary: Arbitrary[Status.Status] = Arbitrary(statusGen)
}
