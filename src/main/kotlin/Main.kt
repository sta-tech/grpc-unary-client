import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import ru.statech.*
import java.io.Closeable
import java.util.concurrent.TimeUnit

typealias JavaFile = java.io.File


class BalanceClient(private val channel: ManagedChannel) : Closeable {
    private val stub: BankServiceGrpcKt.BankServiceCoroutineStub
        = BankServiceGrpcKt.BankServiceCoroutineStub(channel)

    suspend fun getBalance(accountNumber: Int) {
        val request = BalanceCheckRequest.newBuilder().setAccountNumber(accountNumber).build()
        val response = stub.getBalance(request)
        println("Received: ${response.amount}")
    }

    suspend fun withdraw(accountNumber: Int, amount: Int) {
        val request = WithdrawRequest.newBuilder()
            .setAccountNumber(accountNumber)
            .setAmount(amount)
            .build()
        stub.withdraw(request).collect { money ->
            println("Money: ${money.value}")
        }
    }

    suspend fun cachDeposit(accountNumber: Int, money: List<Int>) {
        val newBalance = stub.cashDeposit(flow {
            money.forEach {
                emit(DepositRequest.newBuilder()
                    .setAccountNumber(accountNumber)
                    .setAmount(it)
                    .build())
                delay(1000L)
            }
        })
        println("New balance: ${newBalance.amount}")
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

class FileServiceClient(private val channel: ManagedChannel): Closeable {
    private val stub: FileServiceGrpcKt.FileServiceCoroutineStub
        = FileServiceGrpcKt.FileServiceCoroutineStub(channel);

    suspend fun uploadFile(): FileUploadResponse {
        val inputStream = JavaFile("/Users/estatkovskii/Family_Day_Program.pdf").inputStream()
        val bytes = ByteArray(4096)
        var size: Int
        return stub.upload(flow {
            while (inputStream.read(bytes).also { size = it } > 0) {
                val uploadRequest: FileUploadRequest = FileUploadRequest.newBuilder()
                    .setMetadata(MetaData.newBuilder()
                        .setName("Family_Day_Program.pdf")
                        .setType("pdf")
                        .build())
                    .setFile(File.newBuilder()
                        .setContent(ByteString.copyFrom(bytes, 0, size))
                        .build())
                    .build()
                emit(uploadRequest)
            }
        })
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

suspend fun main() {
    val port = 50051

    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

    /*
    BalanceClient(channel).use {
        it.getBalance(2)
        it.withdraw(2, 500)
        try {
            it.withdraw(2, 1500)
        }
        catch (ex: StatusException) {
            println("Error ${ex.status.code}: ${ex.status.description}")
        }
        it.cachDeposit(2, listOf(100, 200, 300))
    }
    */

    FileServiceClient(channel).use {
        val result = it.uploadFile()
        println("Upload result: ${result.status}, file: ${result.name}")
    }
}