/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.config

import akka.actor.{Cancellable, Scheduler}
import javax.inject.Inject
import org.apache.commons.lang3.time.StopWatch
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Logging}
import uk.gov.hmrc.agentclientmandate.services.MandateUpdateService
import uk.gov.hmrc.play.scheduling.{ExclusiveScheduledJob, ScheduledJob}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DefaultScheduledJobStarter @Inject()(val app: Application,
                                           val applicationLifecycle: ApplicationLifecycle,
                                           val mandateUpdateService: MandateUpdateService) extends ScheduledJobStarter {
  override val scheduledJobs: Seq[ScheduledJob] = Seq(new ExclusiveScheduledJob {
    override def name: String = "ExpirationService"
    override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
      mandateUpdateService.checkStaleDocuments()
      Future.successful(Result("[executeInMutex] Checking expiry of stale documents"))
    }
    override def interval: FiniteDuration = 1 day
    override def initialDelay: FiniteDuration = 0 seconds
  })

  cancellables = scheduledJobs.map { job =>
    scheduler(app).schedule(job.initialDelay, job.interval) {
      val stopWatch = new StopWatch
      stopWatch.start()
      logger.info(s"Executing job ${job.name}")

      job.execute.onComplete {
        case Success(job.Result(message)) =>
          stopWatch.stop()
          logger.info(s"Completed job ${job.name} in $stopWatch: $message")
        case Failure(throwable) =>
          stopWatch.stop()
          logger.error(s"Exception running job ${job.name} after $stopWatch", throwable)
      }
    }
  }
}

trait ScheduledJobStarter extends Logging {
  val scheduledJobs: Seq[ScheduledJob]
  val app: Application
  val applicationLifecycle: ApplicationLifecycle

  private[config] var cancellables: Seq[Cancellable] = Seq.empty
  private[config] def scheduler(app: Application): Scheduler = app.actorSystem.scheduler

  applicationLifecycle.addStopHook { () =>
    logger.info(s"Cancelling all scheduled jobs.")

    Future {
      cancellables.foreach(_.cancel())
      scheduledJobs.foreach { job =>
        logger.info(s"Checking if job ${job.configKey} is running")
        while (Await.result(job.isRunning, 5.seconds)) {
          logger.warn(s"Waiting for job ${job.configKey} to finish")
          Thread.sleep(1000)
        }
        logger.warn(s"Job ${job.configKey} is finished")
      }
    }
  }
}