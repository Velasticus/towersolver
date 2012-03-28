package com.kalulu.sgs.swtor.towersolver.manager

import com.kalulu.sgs.swtor.towersolver.traits.PersistenceManager
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.kalulu.sgs.swtor.towersolver.persistence.ResultHandler
import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait

@serializable
class PersistenceManagerProxy(private val backingManager:PersistenceManager) extends PersistenceManager {

  def servers(callback: ResultHandler[List[ServerTrait]]): Unit = backingManager.servers(callback)

  def getPlayer(id: Int) = backingManager.getPlayer(id)

  def getPlayer(email: String) = backingManager.getPlayer(email)

  def savePlayer(player: Player)= backingManager.savePlayer(player)

}