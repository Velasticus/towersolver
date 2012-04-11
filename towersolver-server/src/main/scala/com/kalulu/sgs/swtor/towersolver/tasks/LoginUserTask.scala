package com.kalulu.sgs.swtor.towersolver.tasks

import com.sun.sgs.app.Task
import com.weiglewilczek.slf4s.Logging
import com.sun.sgs.app.ManagedReference
import java.util.Deque
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.sun.sgs.app.ClientSession
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.util.ScalableDeque
import com.kalulu.sgs.swtor.towersolver.impl.TowerSolverLobby

class LoginUserTask(private val deque : ManagedReference[ScalableDeque[ClientSession]], private val lobby : ManagedReference[TowerSolverLobby] ) extends Task with Logging with Serializable {
  override def run() {
    val chanRef = AppContext.getDataManager.createReference(AppContext.getChannelManager.getChannel("LOBBY_CHAN"))
    val session = deque.getForUpdate.poll
    if ( session != null ) {
      chanRef.get.join(session)
      AppContext.getTaskManager.scheduleTask(this)
    } else {
      AppContext.getTaskManager.scheduleTask(this,500)
    }
  }
}