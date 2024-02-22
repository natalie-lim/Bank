import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

//find out how to periodically siphon off money into a different wallet
// create a second wallet w money im taking for fee, with just your funds
//library has clear documentation on how to transfer
//transfer money to second account
// make a transfer function
//run this at home to download rest of blockchain. then u can see the transfer of satoshis
//check blockCypher to see if it works

//at home, when running code, it will keep logging. the event listener for transactions, it will record/spit out every tranaction
//output other transactions, download rest of blockchain
//received reject tranaction
//u see the money in the faucet?

public class Bank {

    // Constants and Global Variables
    private static final String WALLET_FILE_NAME = "bitcoin-wallet";
    private static final NetworkParameters params = TestNet3Params.get();
    private static final long RECENT_PERIOD = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static List<Transaction> recentTransactions = new ArrayList<>();
    private static WalletAppKit walletAppKit = null;
    private static long lastTransferTime = 0;
    private static final long TRANSFER_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    static {
        // Assuming SLF4J is bound to logback
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.ERROR);
    }

    public static void main(String[] args) throws Exception {
        // Wallet setup
        Wallet wallet = checkOrCreateWallet(params); 
        Wallet personalWallet = checkOrCreateWallet(params);

        // Event listener for transactions
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                long currentTime = System.currentTimeMillis();
                long transactionTime = tx.getUpdateTime().getTime();
                if (currentTime - transactionTime <= RECENT_PERIOD) {
                    recentTransactions.add(tx);
                    System.out.println("New recent transaction: " + tx.getHashAsString());
                }
            }
        });
        
        // Initial setup output
        printWalletAndConnectionInfo(wallet); 

        // Continuous balance check loop
        while (true) {
            System.out.println("Wallet balance (in satoshis): " + wallet.getBalance().value);
            System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
            System.out.println("Peers: " + walletAppKit.peerGroup().getConnectedPeers().size());

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTransferTime >= TRANSFER_INTERVAL) {
            transferMyFee(wallet, personalWallet);
            lastTransferTime = currentTime;
        }

        TimeUnit.SECONDS.sleep(30); // Adjust check interval as needed

            // Optionally, clean up old transactions from the list
            recentTransactions.removeIf(tx -> currentTime - tx.getUpdateTime().getTime() > RECENT_PERIOD);

            TimeUnit.SECONDS.sleep(30); // Adjust check interval as needed
        }
    }

    // Helper Functions
    private static Wallet checkOrCreateWallet(NetworkParameters params) throws IOException, UnreadableWalletException {
        walletAppKit = new WalletAppKit(params, new File("."), WALLET_FILE_NAME);
        walletAppKit.setBlockingStartup(false);
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();        
        walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.001); // Example: 0.1% false positive rate

        System.out.println("Wallet address: " + walletAppKit.wallet().currentReceiveAddress().toString());

        File walletFile = new File(WALLET_FILE_NAME + ".wallet");
        
        if (walletFile.exists()) {
            // Wallet exists, load it
            return Wallet.loadFromFile(walletFile);
        } else {
            Wallet wallet = walletAppKit.wallet();
            wallet.saveToFile(walletFile);
            throw new UnreadableWalletException("Wallet not found, created a new one");
        }
    }

    //transfer function
    private static void transferMyFee(Wallet client, Wallet personal) {
        try {
            Coin balance = client.getBalance();
            Coin fee = balance.multiply((long) 0.01); // 1% of balance

        if (fee.isGreaterThan(Coin.ZERO)) {
            Address destination = personal.currentReceiveAddress();
            Wallet.SendResult sendResult = client.sendCoins(walletAppKit.peerGroup(), destination, fee);
            sendResult.broadcastComplete.get(); // Wait for broadcast to complete
            System.out.println("Transferred " + fee.toFriendlyString() + " to personal wallet.");
        } else {
            System.out.println("Not enough balance for transfer.");
        }
    } catch (Exception e) {
        System.out.println("Transfer failed: " + e.getMessage());
    }
}


    private static void printWalletAndConnectionInfo(Wallet wallet) {
        System.out.println("Initial Balance: " + wallet.getBalance().toFriendlyString());
        System.out.println("Network: " + params.getId());
        System.out.println("Connected peers: " + walletAppKit.peerGroup().getConnectedPeers().size());
        System.out.println("Wallet address: " + wallet.currentReceiveAddress().toString());
        System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
    }
}