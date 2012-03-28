package com.kalulu.sgs.swtor.towersolver.tasks

import scala.collection.mutable.MutableList
import com.kalulu.sgs.swtor.towersolver.persistence.ResultHandler
import com.kalulu.sgs.swtor.towersolver.traits.PersistenceManager
import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait
import com.sun.sgs.app.ManagedReference
import com.sun.sgs.app.Task
import com.weiglewilczek.slf4s.Logging
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.ManagedObject

class UpdateServerTask(val ref : ManagedReference[ServerList]) extends Task with ResultHandler[List[ServerTrait]] with Logging {
  
  var start : Long = 0 
  
  def result(result: List[ServerTrait]): Unit = { 
    val list = ref.getForUpdate()
    list.servers = result
    logger.debug("Updating server list took " + ( compat.Platform.currentTime - start ) + " ms")
  }

  def run(): Unit = {
    val persistence = AppContext.getManager(classOf[PersistenceManager])
    start = compat.Platform.currentTime
    persistence.servers(this)
  }

}

case class ServerList extends ManagedObject {
  var servers = List[ServerTrait]()
}