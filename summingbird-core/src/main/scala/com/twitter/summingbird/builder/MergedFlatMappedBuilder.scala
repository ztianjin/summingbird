package com.twitter.summingbird.builder

import backtype.storm.topology.TopologyBuilder
import backtype.storm.topology.BoltDeclarer

import cascading.flow.FlowDef

import com.twitter.scalding.{TypedPipe, Mode}

import com.twitter.summingbird.FlatMapper
import com.twitter.summingbird.batch.{ Batcher, BatchID }

import com.twitter.summingbird.scalding.ScaldingEnv
import com.twitter.summingbird.service.CompoundService
import com.twitter.summingbird.storm.StormEnv

/**
 *  @author Oscar Boykin
 *  @author Sam Ritchie
 *
 * MergedFlatMappedBuilder describes the union of two individual FlatMappedBuilders.
 */

class MergedFlatMappedBuilder[Time,Key,Value]
(left: FlatMappedBuilder[Time,Key,Value],
 right: FlatMappedBuilder[Time,Key,Value])
extends FlatMappedBuilder[Time,Key,Value] {

  override lazy val eventCodecPairs = left.eventCodecPairs ++ right.eventCodecPairs

  override lazy val sourceBuilders = left.sourceBuilders ++ right.sourceBuilders

  override def addToTopo(env: StormEnv, tb: TopologyBuilder, suffix: String) {
    left.addToTopo(env, tb, suffix + "-L")
    right.addToTopo(env, tb, suffix + "-R")
  }

  override def attach(groupBySumBolt: BoltDeclarer, suffix: String) = {
    val ldec = left.attach(groupBySumBolt, suffix + "-L")
    right.attach(ldec, suffix + "-R")
  }

  override def flatMapBuilder[Key2,Val2](newFlatMapper: FlatMapper[(Key,Value),Key2,Val2])
  : FlatMappedBuilder[Time, Key2, Val2] =
    new MergedFlatMappedBuilder(left.flatMapBuilder(newFlatMapper),
      right.flatMapBuilder(newFlatMapper))

  override def filter(fn: ((Key,Value)) => Boolean) =
    new MergedFlatMappedBuilder(left.filter(fn), right.filter(fn))

  override def getFlatMappedPipe(batcher: Batcher[Time], lowerb: BatchID, env: ScaldingEnv)
  (implicit fd : FlowDef, mode: Mode): TypedPipe[(Time,Key,Value)] = {
    left.getFlatMappedPipe(batcher, lowerb, env) ++ right.getFlatMappedPipe(batcher, lowerb, env)
  }

  override def leftJoin[JoinedValue](service: CompoundService[Key, JoinedValue]) =
    new MergedFlatMappedBuilder(left.leftJoin(service), right.leftJoin(service))
}