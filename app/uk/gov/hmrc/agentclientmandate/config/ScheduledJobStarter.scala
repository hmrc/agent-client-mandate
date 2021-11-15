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

package uk.gov.hmrc.agentclientmandate.config

import akka.actor.{Cancellable, Scheduler}
import org.apache.commons.lang3.time.StopWatch
import play.api.Application
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.agentclientmandate.services.MandateUpdateService
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logInfo, logWarn}
import uk.gov.hmrc.agentclientmandate.utils.{ExclusiveScheduledJob, ScheduledJob}

import javax.inject.Inject
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DefaultScheduledJobStarter @Inject()(val app: Application,
                                           val applicationLifecycle: ApplicationLifecycle,
                                           val mandateUpdateService: MandateUpdateService,
                                           implicit val ec : ExecutionContext) extends ScheduledJobStarter {
  override val scheduledJobs: Seq[ScheduledJob] = Seq(new ExclusiveScheduledJob {
    override def name: String = "ExpirationService"

    override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
      mandateUpdateService.checkStaleDocuments()
      Future.successful(Result("[executeInMutex] Checking expiry of stale documents"))
    }

    override def interval: FiniteDuration = 1 day

    override def initialDelay: FiniteDuration = 0 seconds
  })

  class RunnableTask(job: ScheduledJob) extends Runnable {
    override def run(): Unit = {
      val stopWatch = new StopWatch
      stopWatch.start()
      logInfo(s"Executing job ${job.name}")

      job.execute.onComplete {
        case Success(job.Result(message)) =>
          stopWatch.stop()
          logInfo(s"Completed job ${job.name} in $stopWatch: $message")
        case Failure(throwable) =>
          stopWatch.stop()
          logError(s"Exception running job ${job.name} after $stopWatch", throwable)
      }
    }
  }

  cancellables = scheduledJobs.map(job => scheduler(app).scheduleWithFixedDelay(job.initialDelay, job.interval)(new RunnableTask(job)))
}

trait ScheduledJobStarter {
  implicit val ec: ExecutionContext

  val scheduledJobs: Seq[ScheduledJob]
  val app: Application
  val applicationLifecycle: ApplicationLifecycle

  private[config] var cancellables: Seq[Cancellable] = Seq.empty

  private[config] def scheduler(app: Application): Scheduler = app.actorSystem.scheduler

  applicationLifecycle.addStopHook { () =>
    logInfo(s"Cancelling all scheduled jobs.")

    Future {
      cancellables.foreach(_.cancel())
      scheduledJobs.foreach { job =>
        logInfo(s"Checking if job ${job.configKey} is running")
        while (Await.result(job.isRunning, 5.seconds)) {
          logWarn(s"Waiting for job ${job.configKey} to finish")
          Thread.sleep(1000)
        }
        logWarn(s"Job ${job.configKey} is finished")
      }
    }
  }
}