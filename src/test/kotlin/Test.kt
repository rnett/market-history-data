import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CBOR
import kotlinx.serialization.dumps
import kotlinx.serialization.json.JSON
import kotlinx.serialization.stringify

@Serializable
data class SerializerTest(
    val data1: String,
    val data2: Int,
    val data3: Double
)

@Serializable
data class SerializerTest2(
    val main: SerializerTest,
    val other: List<SerializerTest>
)

@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    val t = SerializerTest("test", 5, 10.14)
    val t2 = SerializerTest2(t, listOf(t.copy(), t.copy(), t.copy()))

    println("Test 1 (data class):")

    val j1 = JSON.plain.stringify(t).length
    val c1 = CBOR.plain.dumps(t).length

    println("JSON length: $j1")
    println("CBOR length: $c1")
    println("% Diff: ${(100 * (c1 - j1) / j1.toDouble()).toInt()}")

    println("Test 2 (data class w/ list):")

    val j2 = JSON.plain.stringify(t2).length
    val c2 = CBOR.plain.dumps(t2).length

    println("JSON length: $j2")
    println("CBOR length: $c2")
    println("% Diff: ${(100 * (c2 - j2) / j2.toDouble()).toInt()}")

}