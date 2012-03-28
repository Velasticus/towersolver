package com.kalulu.sgs.swtor.towersolver

import com.weiglewilczek.slf4s.Logging
import com.sun.sgs.app.ManagedReference
import com.sun.sgs.app.ManagedObject
import com.sun.sgs.app.Task

@serializable
class TowerSolverLobby(private val servers : ManagedReference[List[String]]) extends ManagedObject with Task with Logging {

  def run : Unit = {
    
  }
}