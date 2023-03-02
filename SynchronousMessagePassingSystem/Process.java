import java.util.*;
import java.net.*;
import java.io.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class Process {
    // Process Fields
    private static int nodeUID;
    private static int port;
    private static ArrayList<Integer> neighbor_uids;
    private static ArrayList<Integer> neighbor_ports;
    private static ArrayList<String> neighbor_hostnames;
    private static HashMap<Integer, Integer> uid_to_index;
    private static MessageReceiver messageReceiver;
    private static ArrayList<MessageSender> messageSenders;
    private static ArrayList<ConnectedClient> connectedClients;
    private static PriorityBlockingQueue<Message> messageMailbox; // Thread safe Concurrent Queue, prioritizing by earliest round (synchronizer)
    private static int number_of_neighbors;

    // Peleg's Leader Election Fields
    private static boolean isLeader;

    // BFS Construction Fields
    private static boolean marked;
    private static int parentUID;
    private static ArrayList<Integer> childrenUIDs;

    static class Message implements Comparable<Message>, Serializable {
        private final static long serialVersionUID = 1;
        public int senderUID = 0;
        public int bestUID = 0;
        public int round = 0;
        public int distance = 0;
        public String purpose = ""; // "Flood", "Termination", "Flood/NACK", "ACK"

        public Message(int senderUID, int bestUID, int round, int distance, String purpose) { // for peleg
            this.senderUID = senderUID;
            this.bestUID = bestUID;
            this.round = round;
            this.distance = distance;
            this.purpose = purpose;
        }

        public Message(int senderUID, String purpose){ // for BFS
            this.senderUID = senderUID;
            this.purpose = purpose;
        }

        public int getSenderUID(){ return this.senderUID; }
        public int getBestUID(){ return this.bestUID; }
        public int getRound(){ return this.round; }
        public int getDistance(){ return this.distance; }
        public String getPurpose(){ return this.purpose; }

        public String toString(){ // just used for debugging. serialization uses the get() methods
            return "SUID:" + this.senderUID + " BUID:" + this.bestUID + " round:" + this.round + " Distance:" + this.distance + " Purpose:" + this.purpose;
        }

        // Custom Comparator for the messageMailbox
        @Override
        public int compareTo(Message msg) {
            if (this.round < msg.round) {
                return -1;
            }
            if (this.round > msg.round) {
                return 1;
            }
            return 0;
        }
    }

    static class ConnectedClient implements Runnable {
        private Socket connected;
        private ObjectInputStream in;

        public ConnectedClient(Socket connected) throws IOException {
            System.out.println("Creating Connected Client ");
            this.connected = connected;
            in = new ObjectInputStream(connected.getInputStream());
            Thread t = new Thread(this);
            t.start();
        }

        public void run() {
            System.out.println("Running Connected Client");
            while (true) {
                try {
                    Object o = in.readObject();
                    if(o instanceof String){
                        System.out.println("First Message Received: " + (String)o);
                    }
                    else{
                        Message m = (Message)o;
                        System.out.println("Received Message [" + m + "] in Queue");
                        messageMailbox.add(m);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    static class MessageReceiver implements Runnable {
        private ServerSocket serverSocket;

        public MessageReceiver() throws IOException {
            System.out.println("Creating Receiver");
            try {serverSocket = new ServerSocket(port); }
            catch (IOException e) { e.printStackTrace(); }
            Thread internal = new Thread(this);
            internal.start();
        }

        public void run() {
            System.out.println("Running Receiver");
            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    connectedClients.add(new ConnectedClient(client));
                }
                catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    static class MessageSender {
        private Socket clientSocket;
        private ObjectOutputStream out;
        public MessageSender(int neighbor_idx) throws IOException {
            try {
                clientSocket = new Socket(neighbor_hostnames.get(neighbor_idx), neighbor_ports.get(neighbor_idx));
                System.out.println("Created sender " + neighbor_idx + " connected to " + clientSocket.getRemoteSocketAddress());
                out = new ObjectOutputStream(clientSocket.getOutputStream());
            }
            catch (IOException e) { e.printStackTrace(); }

            try {
                String test = "Initial Connection Established with " + nodeUID;
                out.writeObject(test);
                out.flush();
            }

            catch (Exception e) { e.printStackTrace(); }
        }

        public ObjectOutputStream getOutputStream(){
            return out;
        }

    }

    public static void main(String[] args) {
        nodeUID = Integer.parseInt(args[0].trim());
        port = Integer.parseInt(args[2].trim());
        neighbor_uids = new ArrayList<>();
        neighbor_ports = new ArrayList<>();
        neighbor_hostnames = new ArrayList<>();
        connectedClients = new ArrayList<>();
        messageSenders = new ArrayList<MessageSender>();
        messageMailbox = new PriorityBlockingQueue<>();
        uid_to_index = new HashMap<>();
        marked = false;
        childrenUIDs = new ArrayList<>();
        parentUID = -1;

        loadNeighbors();

        number_of_neighbors = neighbor_uids.size();

        
        try {
            System.out.println("NodeUID: " + nodeUID + " Listening Port: " + port);
            for(int i = 0; i < number_of_neighbors; i++){
                uid_to_index.put(neighbor_uids.get(i), i);
                System.out.println("Neighbor: UID: " + neighbor_uids.get(i) + " Hostname: " + neighbor_hostnames.get(i) + " Port: " + neighbor_ports.get(i));
            }

            messageReceiver = new MessageReceiver();

            try {Thread.sleep(9999);}
            catch (Exception e) { e.printStackTrace(); }

            for(int i = 0; i < number_of_neighbors; i++){
                MessageSender messageSender = new MessageSender(i);
                messageSenders.add(messageSender);
            }

            try {Thread.sleep(9999);}
            catch (Exception e) { e.printStackTrace(); }

            peleg();

            try {Thread.sleep(9999);}
            catch (Exception e) { e.printStackTrace(); }

            System.out.println("Leader: " + isLeader);
            messageMailbox.clear();

            try {Thread.sleep(9999);}
            catch (Exception e) { e.printStackTrace(); }

            BFS();

            try {Thread.sleep(9999);}
            catch (Exception e) { e.printStackTrace(); }

            System.out.println("**Final Output**");
            System.out.println("BFS Tree Constructed");
            if(isLeader)
              System.out.println("Root of Tree, no parents");
            else
              System.out.println("Parent UID: " + parentUID);
            System.out.println("Children UIDS: " + childrenUIDs.toString());
            System.out.println("Degree: " + ((isLeader ? 0 : 1) + childrenUIDs.size()));


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Send a message to neighbors, used to flood in Peleg's
    public static void sendToAllNeighbors(Message m){
        for(MessageSender sender: messageSenders){
            try{
                ObjectOutputStream out = sender.getOutputStream();
                out.writeObject(m);
                out.flush();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    // Send a message to neighbors except the parent, used in BFS
    public static void sendToAllNeighborsExceptParent(Message m, int parentUID){
        for(int i = 0; i < number_of_neighbors; i++){
            if(neighbor_uids.get(i) == parentUID)
                continue;
            try{
                ObjectOutputStream out = messageSenders.get(i).getOutputStream();
                out.writeObject(m);
                out.flush();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    // Send a specific message, used in BFS for ACK
    public static void sendToSpecificNeighbor(Message m, int neighborUID){
        try{
            ObjectOutputStream out = messageSenders.get(uid_to_index.get(parentUID)).getOutputStream();
            out.writeObject(m);
            out.flush();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    // Peleg's Leader Election Algorithm
    public static void peleg(){
        int pelegBestUID = nodeUID;
        int pelegMaxDistance = 0;
        int roundsWithNoChange = 0;
        int round = 0;
        
        while(roundsWithNoChange < 3){
            int messagesReceived = 0;
            boolean updated = false;
            Message pelegData = new Message(nodeUID, pelegBestUID, round, pelegMaxDistance, "Flood");
            sendToAllNeighbors(pelegData);

            while(messagesReceived < number_of_neighbors){
                if(!messageMailbox.isEmpty() && messageMailbox.peek().round == round) {
                    Message received = messageMailbox.poll();
                    if(received.bestUID > pelegBestUID){
                        if(pelegMaxDistance < received.distance + 1) {
                            pelegMaxDistance = received.distance + 1;
                            roundsWithNoChange = 0;
                            updated = true;
                        }
                        pelegBestUID = received.bestUID;
                    }
                    messagesReceived++;
                }
            }
            if(!updated){
                roundsWithNoChange++;
            }
            round++;
        }

        isLeader = (nodeUID == pelegBestUID) ? true : false;

        Message terminationData = new Message(nodeUID, pelegBestUID, round, -1, "Termination");
        sendToAllNeighbors(terminationData);
    }

    public static void BFS(){
        int messagesReceived = 0;

        if(!isLeader){
            while(messageMailbox.isEmpty()){ // wait for flood message to wake up node
            }
            Message firstMessage = messageMailbox.poll();
            parentUID = firstMessage.senderUID;
            sendToSpecificNeighbor(new Message(nodeUID, "ACK"), parentUID);
            messagesReceived++;
        }

        marked = true;
        sendToAllNeighborsExceptParent(new Message(nodeUID, "Flood/NACK"), parentUID); // Flood message if unmarked, NACK if marked

        // process messages
        while(messagesReceived < number_of_neighbors){
            if(!messageMailbox.isEmpty()){
                Message received = messageMailbox.poll();
                System.out.println("Message opened in BFS: [" + received + "]");
                messagesReceived++;
                if(received.purpose.equals("Flood/NACK")) // This is a NACK message, ignore it
                    continue;
                if(received.purpose.equals("ACK"))
                    childrenUIDs.add(received.senderUID);
            }
        }
    }

    public static void loadNeighbors(){
        int configIndex = -1; // to determine which line in the neighbor list we need to read
        int n = -1; // number of processes, used for reading
        
        HashMap<Integer, Integer> uid_to_port = new HashMap<>(); // for config set up (before socket connection)
        HashMap<Integer, String> uid_to_hostname = new HashMap<>();
        BufferedReader objReader = null;
    
        try {
         String strCurrentLine;
         objReader = new BufferedReader(new FileReader("/home/013/t/ts/tsh160230/6380-launch/config.txt"));
         
         int i = 0; // counter for list a in config
         int j = 0; // counter for list b in config
         
         while ((strCurrentLine = objReader.readLine()) != null) {
           if(strCurrentLine.equals("") || strCurrentLine.charAt(0) == '#')
             continue;
           if(n == -1)
             n = Integer.parseInt(strCurrentLine);
           else if(n != -1 && i < n){
             String[] line_tokens = strCurrentLine.split(" ", 0);

             int curID = Integer.parseInt(line_tokens[0]);
             String hostName = line_tokens[1];
             int curPort = Integer.parseInt(line_tokens[2]);

             uid_to_port.put(curID, curPort);
             uid_to_hostname.put(curID, hostName);

             if(curID == nodeUID){
               configIndex = i;
             }   
             i++;  
           }
           else if(n != -1 && i == n){
             if(j == configIndex){
               String[] neighbors = strCurrentLine.split(" ", 0);
               for(String neighbor: neighbors){
                 neighbor_uids.add(Integer.parseInt(neighbor));
               }
             }
             j++;
           }
          }    
        } catch (IOException e) {
         e.printStackTrace();
        } finally {
         try {
          if (objReader != null)
           objReader.close();
         } catch (IOException ex) {
          ex.printStackTrace();
         }
        }
        
        for(int neighbor_id: neighbor_uids){
          neighbor_ports.add(uid_to_port.get(neighbor_id));
          neighbor_hostnames.add(uid_to_hostname.get(neighbor_id));
        }
    }
}