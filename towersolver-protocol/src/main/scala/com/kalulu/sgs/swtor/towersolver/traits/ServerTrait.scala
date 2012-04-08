package com.kalulu.sgs.swtor.towersolver.traits

import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerRegion
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerType
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerContinent
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerStatus
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerPopulation
import com.kalulu.sgs.swtor.towersolver.protocol.server.SingleServer

trait ServerTrait {

  def name : String
  def serverType : ServerType
  def region : ServerRegion
  def continent : ServerContinent
  var population : ServerPopulation
  var status : ServerStatus
  
  def singleServer : SingleServer
  
}
