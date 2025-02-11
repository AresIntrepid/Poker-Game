// import necessary Java libraries
import java.net.Socket;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Scanner;

//represents a poker game client that can connect to a server or run in test mode for simulated game play
public class Poker {
    private Socket socket; //socket for network communication
    private DataInputStream dis; //reading data from the server
    private DataOutputStream dos; //sending data to the server
    private boolean testMode; //set to indicate if running in test mode
    private Scanner scanner; //reading input in test mode
    private String[] visibleCards; // array to store all visible cards in the game
    private int cardCount; // keeps track of how many cards are currently stored in the visibleCards array
    private static final int MAX_CARDS = 52; // maximum size for the visibleCards array (standard deck size)

    //Constructor for the Poker class
    //IpAddress: The IP address of the server to connect to
    //IpPort: The port number of the server
    //testMode: Whether to run in test mode (true) or connect to a real server (false)
    public Poker(String IpAddress, int IpPort, boolean testMode) throws IOException {
        this.testMode = testMode;
        this.visibleCards = new String[MAX_CARDS]; // initialize the array to store visible cards with maximum deck size
        this.cardCount = 0; // initialize the count of visible cards to 0
        if (!testMode) {
            // Set up network connection if not in test mode
            socket = new Socket(IpAddress, IpPort);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        } else {
            // Set up scanner for test mode input
            scanner = new Scanner(System.in);
        }
    }

    //Writes a string to the server or console in test mode
    private void write(String s) throws IOException {
        if (!testMode) {
            dos.writeUTF(s);
            dos.flush();
        } else {
            System.out.println("Client sent: " + s);
        }
    }

    //Reads a string from the server or console in test mode
    private String read() throws IOException {
        if (!testMode) {
            return dis.readUTF();
        } else {
            System.out.print("Enter server command: ");
            return scanner.nextLine();
        }
    }

    //Main game loop that processes commands from the server
    public void playGame() throws IOException {
        while (true) {
            String command = read();
            String[] commandParts = command.split(":");

            // Process different types of commands
            if (commandParts[0].equals("login")) {
                write("AresIntrepid:Ares");  // Send login credentials
            } else if (commandParts[0].equals("bet1") || commandParts[0].equals("bet2")) {
                handleBetting(commandParts);  // Handle betting rounds
            } else if (commandParts[0].equals("status")) {
                processStatus(commandParts);  // Process game status updates
            } else if (commandParts[0].equals("done")) {
                System.out.println("Game over");
                break;  // Exit the loop when the game is done
            }
        }

        // close open resources
        if (!testMode && socket != null) {
            socket.close(); // Close the socket if not in test mode and the socket is initialized
        }
        if (testMode && scanner != null) {
            scanner.close(); // Close the scanner if in test mode and the scanner is initialized
        }
    }

    //Handles the betting logic for both first and second betting rounds
    //commandParts: Array of command commandParts containing game state information
    private void handleBetting(String[] commandParts) throws IOException {
        int stack = Integer.parseInt(commandParts[1]); // Player's current chip stack
        int pot = Integer.parseInt(commandParts[2]); // Current pot size
        int currentBet = Integer.parseInt(commandParts[3]); // Current bet to call
        String holeCard = commandParts[4]; // Player's hole card
        String firstUpCard = commandParts[5]; // First up card

        boolean shouldBet = false;
        int betAmount = 0;

        // Evaluate hand and decide whether to bet
        if (commandParts[0].equals("bet1")) {
            shouldBet = evaluateBet1Hand(holeCard, firstUpCard, commandParts);
        } else { // bet2
            String secondUpCard = commandParts[6];
            shouldBet = evaluateBet2Hand(holeCard, firstUpCard, secondUpCard, commandParts);
        }

        // Determine action based on evaluation and available chips
        if (shouldBet && stack > currentBet) {
            // Bet between currentBet and currentBet + 10, but not more than available stack
            // ensures that the player cannot bet more than they actually have
            // ensures that the bet amount is at least equal to the current bet
            betAmount = Math.max(currentBet, Math.min(currentBet + 10, stack));
            write("bet:" + betAmount);
        } else if (stack > 0) {
            // Call if we have chips but don't want to bet
            write("bet:" + currentBet);
        } else {
            // Fold if we have no chips
            write("fold");
        }
    }

    //Evaluates whether to bet in the first betting round.
    //return true if the hand is strong enough to bet, false otherwise
    private boolean evaluateBet1Hand(String holeCard, String upCard, String[] commandParts) {
        // bet if we have a pair or high cards
        char holeRank = holeCard.charAt(0);
        char upRank = upCard.charAt(0);

        // check if the hole card rank is the same as the up card rank(a pair)
        boolean hasPair = holeRank == upRank; // hasPair is true if holeRank equals upRank

        // check if either the hole card or the up card is a high card
        boolean hasHighCard = isHighCard(holeRank) || isHighCard(upRank); // hasHighCard is true if either holeRank or upRank is a high card

        // track visible cards for future reference
        trackOpponentCards(commandParts);

        // return true if there is a pair or if there is at least one high card
        return hasPair || hasHighCard; // The method returns true if either hasPair or hasHighCard is true
    }

    //evaluates whether to bet in the second betting round.
    //return true if the hand is strong enough to bet, false otherwise 
    private boolean evaluateBet2Hand(String holeCard, String firstUpCard, String secondUpCard, String[] commandParts) {
        //bet if we have a pair or better
        // Extract the rank of the hole card from its string representation
        char holeRank = holeCard.charAt(0); // holds the first character of holeCard (rank)
        // Extract the rank of the first up card from its string representation
        char firstUpRank = firstUpCard.charAt(0); //holds the first character of the first up card (rank)
        //Extract the rank of the second up card from its string representation
        char secondUpRank = secondUpCard.charAt(0); //holds the first character of the second up card (rank)

        //checks if any two cards among the hole card and the two up cards have the same rank
        //holeRank == firstUpRank: Checks if the hole card has the same rank as the first up card
        //holeRank == secondUpRank: Checks if the hole card has the same rank as the second up card
        //firstUpRank == secondUpRank: Checks if the first up card has the same rank as the second up card
        //If any of these conditions are true, hasPair will be true, indicating at least one pair exists
        boolean hasPair = holeRank == firstUpRank || holeRank == secondUpRank || firstUpRank == secondUpRank;
        //if both conditions are true, it means all three cards have the same rank and we have 3 of a kind
        boolean hasThreeOfAKind = holeRank == firstUpRank && holeRank == secondUpRank;

        // track visible cards for future reference
        trackOpponentCards(commandParts);

        // return true if we have a pair, three of a kind, or the highest spade
        return hasPair || hasThreeOfAKind || hasHighSpadeInHole(holeCard);
    }

    //Tracks and stores all visible cards from the current game state
    //Updates the visibleCards array with current visible cards
    //commandParts: Array containing the game state information including visible cards
    private void trackOpponentCards(String[] commandParts) {
        // Reset the card tracking for new round
        cardCount = 0;
        
        // Find the index after "up" in the command array to start processing cards
        int upCardIndex = indexOf("up", commandParts) + 1;
        // Continue processing cards until the end of the command array
        while (upCardIndex < commandParts.length) {
            // Only process non-empty card strings
            if (!commandParts[upCardIndex].equals("")) {
                visibleCards[cardCount] = commandParts[upCardIndex]; // store the card
                cardCount++; // increment the count of stored cards
            }
            upCardIndex++; // move to next potential card
        }
    }

    //Finds the index of a target string within an array
    //target: The string to search for
    //array: The array to search within
    //returns: The index of the target string, or -1 if not found
    private int indexOf(String target, String[] array) {
        // Iterate through the array looking for the target string
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(target)) {
                return i; // return index when target is found
            }
        }
        return -1; // return -1 if target is not found in array
    }

    //Checks if the hole card is the highest spade currently visible
    //holeCard: The player's hole card to evaluate
    //returns: true if the hole card is a spade and no higher spade is visible, false otherwise
    private boolean hasHighSpadeInHole(String holeCard) {
        // First check if the hole card is even a spade
        if (!holeCard.endsWith("S")) {
            return false; // return false if the hole card is not a spade
        }
        
        // Get the rank of our spade hole card
        char holeRank = holeCard.charAt(0);
        
        // Check all visible cards to see if there's a higher spade
        for (int i = 0; i < cardCount; i++) {
            // If we find a spade with a higher rank than our hole card
            if (visibleCards[i].endsWith("S") && visibleCards[i].charAt(0) > holeRank) {
                return false; // return false as our spade is not the highest
            }
        }
        // If we haven't found any higher spades, our card is the highest visible spade
        return true;
    }

    //Determines if a card rank is considered high (10 or face card)
    //rank: The rank of the card
    //true if the rank is high, otherwise it is false
    private boolean isHighCard(char rank) {
        // A is Ace, highest card
        // K is King, 2nd highest card
        // Q is Queen, 3rd highest card
        // J is Jack, 4th highest card
        // T is Ten, considered a high card potentially
        return rank == 'A' || rank == 'K' || rank == 'Q' || rank == 'J' || rank == 'T';
    }

    //Processes and displays the status message from the server
    // commandParts: array containing the segments of the command + additional info
    private void processStatus(String[] commandParts) {
        String result = "";
        for (int i = 0; i < commandParts.length; i++) {
            result += commandParts[i];
            if (i < commandParts.length - 1) {
                result += ":";
            }
        }
        System.out.println("Hand result: " + result);
    }

    // Main method to run the Poker game client
    //args command line arguments: [IP address] [Port] [test (optional)]
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            // Display usage instructions if not enough arguments are provided
            System.out.println("*** Poker Game Instructions ***");
            System.out.println("Follow these instructions to run the game:");
            System.out.println("\nNecessary Information:");
            System.out.println("1. IP Address: The address of the game server");
            System.out.println("2. Port: The port number on the server");
            System.out.println("3. Mode: Add 'test' to play in a simulated mode");
            System.out.println("\nExample Command format:");
            System.out.println("java Poker <IP_Address> <Port> [test]");
            System.out.println("\nExamples:");
            System.out.println("1. Normal mode:  java Poker localhost 12345");
            System.out.println("2. Test mode:    java Poker localhost 12345 test");
            System.out.println("\nEnjoy the Game!");
            return;
        }

        // Get the ip address from the cmd line arg
        // holds the first argument
        String IpAddress = args[0];

        //Get the port number from the cmd line arg and convert it to an integer
        //holds the second argument
        int IpPort = Integer.parseInt(args[1]);

        // determines if running in test mode based on the 3rd cmd line arg
        //testMode is true if the 3rd arg is "test"
        boolean testMode = false; // initialize testMode to false by default
        if (args.length > 2) {
            if (args[2].equalsIgnoreCase("test")) {
                testMode = true; // set testMode to true only if the third argument is "test" (case insensitive)
            }
        }

        // Create and run the Poker game
        Poker poker = new Poker(IpAddress, IpPort, testMode);
        poker.playGame();
    }
}