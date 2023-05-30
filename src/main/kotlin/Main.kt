import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import ru.statech.BalanceCheckRequest
import ru.statech.BankServiceGrpcKt
import ru.statech.DepositRequest
import ru.statech.WithdrawRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit

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

suspend fun main() {
    val port = 50051

    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

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
}