package com.kalulu.sgs.swtor.towersolver.impl

import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait
import com.kalulu.sgs.swtor.towersolver.protocol.server.SingleServer
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerStatus
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerPopulation

case class ServerImpl(val server : SingleServer) extends ServerTrait {

  override def name : String = server.getName.toString
  override def serverType = server.getType
  override def region = server.getRegion
  override def continent = server.getContinent
  override def population = server.getPopulation
  override def population_= (population:ServerPopulation) {server.setPopulation(population)}
  override def status = server.getStatus
  override def status_= (status:ServerStatus) {server.setStatus(status)}
  
}