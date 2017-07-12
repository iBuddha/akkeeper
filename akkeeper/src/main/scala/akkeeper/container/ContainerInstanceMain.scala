/*
 * Copyright 2017 Iaroslav Zeigerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package akkeeper.container

import java.io.File

import akka.actor.{ActorSystem, AddressFromURIString}
import akkeeper.common._
import akkeeper.container.service.ContainerInstanceService
import akkeeper.storage.InstanceStorageFactory
import akkeeper.utils.CliArguments._
import akkeeper.utils.ConfigUtils._
import akkeeper.utils.yarn.LocalResourceNames
import com.typesafe.config.{Config, ConfigFactory}
import scopt.OptionParser

import scala.io.Source
import scala.util.control.NonFatal
import spray.json._
import ContainerDefinitionJsonProtocol._
import ContainerInstanceService._
import org.slf4j.LoggerFactory

object ContainerInstanceMain extends App {
  val logger = LoggerFactory.getLogger(ContainerInstanceMain.getClass)

  val optParser = new OptionParser[ContainerInstanceArguments]("akkeeperInstance") {
    head("akkeeperInstance", "0.1")

    opt[String](AppIdArg).required().action((v, c) => {
      c.copy(appId = v)
    }).text("ID of this application")

    opt[String](InstanceIdArg).required().action((v, c) => {
      c.copy(instanceId = InstanceId.fromString(v))
    }).text("ID of this instance")

    opt[String](MasterAddressArg).required().action((v, c) => {
      c.copy(masterAddress = AddressFromURIString.parse(v))
    }).text("master instance address")

    opt[File](ActorLaunchContextsArg).required().action((v, c) => {
      c.copy(actors = v)
    }).text("actor launch context in JSON format")

    opt[File](ConfigArg).valueName("<file>").optional().action((v, c) => {
      c.copy(userConfig = Some(ConfigFactory.parseFile(v)))
    }).text("configuration file")

    opt[String](PrincipalArg).valueName("principal").optional().action((v, c) => {
      c.copy(principal = Some(v))
    })
  }

  def createInstanceConfig(instanceArgs: ContainerInstanceArguments): Config = {
    val baseConfig = instanceArgs.userConfig
      .map(_.withFallback(ConfigFactory.load()))
      .getOrElse(ConfigFactory.load())
    instanceArgs.principal
      .map(p => baseConfig.withPrincipalAndKeytab(p, LocalResourceNames.KeytabName))
      .getOrElse(baseConfig)
  }

  def run(instanceArgs: ContainerInstanceArguments): Unit = {
    val instanceConfig = createInstanceConfig(instanceArgs)
    val actorSystem = ActorSystem(instanceConfig.getActorSystemName, instanceConfig)
    val zkConfig = actorSystem.settings.config.getZookeeperClientConfig
    val instanceStorage = InstanceStorageFactory.createAsync(zkConfig.child(instanceArgs.appId))
    val actorsJsonStr = Source.fromFile(instanceArgs.actors).getLines().mkString("\n")
    val actors = actorsJsonStr.parseJson.convertTo[Seq[ActorLaunchContext]]

    val containerInstanceService = ContainerInstanceService.createLocal(actorSystem,
      instanceStorage, instanceArgs.instanceId, instanceArgs.masterAddress)
    containerInstanceService ! LaunchActors(actors)

    actorSystem.awaitTermination()
    sys.exit(0)
  }

  try {
    logger.info("ContainerInstanceMain starting")
    optParser.parse(args, ContainerInstanceArguments()).foreach(run)
  } catch {
    case NonFatal(e) =>
      e.printStackTrace()
      sys.exit(1)
  }
}
