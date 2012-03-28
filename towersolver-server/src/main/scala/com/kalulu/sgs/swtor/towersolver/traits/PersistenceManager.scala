package com.kalulu.sgs.swtor.towersolver.traits

import com.kalulu.sgs.swtor.towersolver.persistence.ResultHandler

trait PersistenceManager {

  def servers(callback:ResultHandler[List[ServerTrait]])
  
  def getPlayer( id : Int ) : Player
  def getPlayer( email : String ) : Player
  
  def savePlayer(player:Player)
  
}