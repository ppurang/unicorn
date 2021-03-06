/*******************************************************************************
  * (C) Copyright 2015 ADP, LLC.
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
  *******************************************************************************/

package unicorn.unibase.graph

import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import unicorn.bigtable.accumulo.Accumulo
import unicorn.json._
import unicorn.oid.Snowflake
import unicorn.unibase._

/**
 * @author Haifeng Li
 */
class GraphSpec extends Specification with BeforeAfterAll {
  // Make sure running examples one by one.
  // Otherwise, test cases on same columns will fail due to concurrency
  sequential
  val bigtable = Accumulo()
  val db = new Unibase(bigtable)
  val graphName = "unicorn_unibase_graph_test"

  var saturn = 0L
  var sky = 0L
  var sea = 0L
  var jupiter = 0L
  var neptune = 0L
  var hercules = 0L
  var alcmene = 0L
  var pluto = 0L
  var nemean = 0L
  var hydra = 0L
  var cerberus = 0L
  var tartarus = 0L

  override def beforeAll = {
    db.createGraph(graphName)

    val gods = db.graph(graphName, new Snowflake(0))

    saturn = gods.addVertex(json"""{"label": "titan", "name": "saturn", "age": 10000}""")
    sky = gods.addVertex(json"""{"label": "location", "name": "sky"}""")
    sea = gods.addVertex(json"""{"label": "location", "name": "sea"}""")
    jupiter = gods.addVertex(json"""{"label": "god", "name": "jupiter", "age": 5000}""")
    neptune = gods.addVertex(json"""{"label": "god", "name": "neptune", "age": 4500}""")
    hercules = gods.addVertex(json"""{"label": "demigod", "name": "hercules", "age": 30}""")
    alcmene = gods.addVertex(json"""{"label": "human", "name": "alcmene", "age": 45}""")
    pluto = gods.addVertex(json"""{"label": "god", "name": "pluto", "age": 4000}""")
    nemean = gods.addVertex(json"""{"label": "monster", "name": "nemean"}""")
    hydra = gods.addVertex(json"""{"label": "monster", "name": "hydra"}""")
    cerberus = gods.addVertex(json"""{"label": "monster", "name": "cerberus"}""")
    tartarus = gods.addVertex(json"""{"label": "location", "name": "tartarus"}""")

    gods.addEdge(jupiter, "father", saturn)
    gods.addEdge(jupiter, "lives", sky, json"""{"reason": "loves fresh breezes"}""")
    gods.addEdge(jupiter, "brother", neptune)
    gods.addEdge(jupiter, "brother", pluto)

    gods.addEdge(neptune, "lives", sea, json"""{"reason": "loves waves"}""")
    gods.addEdge(neptune, "brother", jupiter)
    gods.addEdge(neptune, "brother", pluto)

    gods.addEdge(hercules, "father", jupiter)
    gods.addEdge(hercules, "mother", alcmene)
    gods.addEdge(hercules, "battled", nemean, json"""{"time": 1, "place": {"latitude": 38.1, "longitude": 23.7}}""")
    gods.addEdge(hercules, "battled", hydra, json"""{"time": 2, "place": {"latitude": 37.7, "longitude": 23.9}}""")
    gods.addEdge(hercules, "battled", cerberus, json"""{"time": 12, "place": {"latitude": 39.0, "longitude": 22.0}}""")

    gods.addEdge(pluto, "brother", jupiter)
    gods.addEdge(pluto, "brother", neptune)
    gods.addEdge(pluto, "lives", tartarus, json"""{"reason": "no fear of death"}""")
    gods.addEdge(pluto, "pet", cerberus)

    gods.addEdge(cerberus, "lives", tartarus)
  }

  override def afterAll = {
    db.dropGraph(graphName)
  }

  "Graph" should {
    "get vertex" in {
      val gods = db.graph(graphName)
      val vertex = gods(alcmene)
      vertex.id === alcmene
      vertex.properties === json"""{"_id": $alcmene, "label": "human", "name": "alcmene", "age": 45}"""
      vertex.inE("mother") === Seq(Edge(hercules, "mother", alcmene, JsNull))
      vertex.inE.size === 1
      vertex.outE.isEmpty === true
    }
    "update vertex" in {
      val gods = db.graph(graphName, new Snowflake(0))
      val update = json"""
                          {
                            "_id": $alcmene,
                            "$$set": {
                              "gender": "female"
                            },
                            "$$unset": {
                              "age": 1
                            }
                          }
                       """
      gods.update(update)

      val vertex = gods(alcmene)
      vertex.properties === json"""{"_id": $alcmene, "label": "human", "name": "alcmene", "gender": "female"}"""
    }
    "delete vertex" in {
      val gods = db.graph(graphName, new Snowflake(0))
      gods.deleteVertex(alcmene)
      gods(alcmene) should throwA[IllegalArgumentException]

      val vertex = gods(hercules)
      vertex.id === hercules
      vertex.outE("mother") should throwA[NoSuchElementException]
    }
    "add document vertex" in {
      val gods = db.graph(graphName, new Snowflake(0))
      db.createTable("doc_vertex_test")
      val table = db("doc_vertex_test")
      val key = table.upsert(json"""{"name": "Tao"}""")
      val docv = gods.addVertex("doc_vertex_test", key)
      val vertex = gods(docv)
      vertex.id === docv
      vertex.properties === json"""{"_id": $docv, "_doc": {"_table": "doc_vertex_test", "_id": "$key"}}"""
      vertex.inE.isEmpty === true
      vertex.outE.isEmpty === true
    }
    "delete document vertex" in {
      val gods = db.graph(graphName, new Snowflake(0))
      val table = db("doc_vertex_test")
      val key = table.upsert(json"""{"name": "Tao"}""")
      val docv = gods.addVertex("doc_vertex_test", key)
      gods.deleteVertex("doc_vertex_test", key)

      gods(docv) should throwA[IllegalArgumentException]
      gods("doc_vertex_test", key) should throwA[IllegalArgumentException]
    }
    "get edge" in {
      val gods = db.graph(graphName)
      gods(neptune, "lives", sea) === Some(json"""{"reason": "loves waves"}""")
      gods(neptune, "lives", jupiter) === None
      gods(neptune, "brother", jupiter) === Some(JsNull)
    }
    "delete edge" in {
      val gods = db.graph(graphName, new Snowflake(0))
      gods.deleteEdge(neptune, "lives", sea)
      gods(neptune, "lives", sea) === None
    }
  }
}
