import java.util.*;
import java.io.*;
import java.nio.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicLong;

public class Process {

    private AtomicLong time = new AtomicLong(0);
    private Map<Integer, InetSocketAddress> map = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int n;
        try {
            n = Integer.parseInt(args[0]);
        } catch (Exception e) {
            System.err.println("Wrong args");
            return;
        }
        new Process().run(n);
    }

    private void run(int n) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("process.cfg"))) {
            String s;
            int i = 1;
            while ((s = reader.readLine()) != null) {
                String[] address = s.split("[=:]");
                map.put(i++, new InetSocketAddress(address[1], Integer.parseInt(address[2])));
            }
        } catch (IOException e) {
            System.err.println("Can't read process.cfg");
            return;
        } catch (NumberFormatException e) {
            System.err.println("Wrong process.cfg file");
            return;
        }
        Selector selector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(map.get(n));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        new Thread(() -> readFromConsole(n, System.in)).start();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (true) {
            selector.select();
            for (Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); iterator.hasNext(); ) {
                SelectionKey key = iterator.next();
                try {
                    if (key.isAcceptable()) {
                        serverSocket = (ServerSocketChannel) key.channel();
                        SocketChannel socket = serverSocket.accept();
                        socket.configureBlocking(false);
                        socket.register(selector, SelectionKey.OP_READ);
                    }
                    if (key.isReadable()) {
                        try (SocketChannel socket = (SocketChannel) key.channel()) {
                            socket.read(buffer);
                            buffer.flip();
                            int id;
                            int msg;
                            long t;
                            try {
                                id = buffer.getInt();
                                msg = buffer.getInt();
                                t = buffer.getLong();
                            } catch (Exception e) {
                                System.err.println("Corrupted message");
                                continue;
                            }
                            t = time.accumulateAndGet(t, (e1, e2) -> Math.max(e1, e2) + 1);
                            System.out.println("received from:" + id + " msg:" + msg + " time:" + t);
                            buffer.clear();
                        }
                    }
                } finally {
                    iterator.remove();
                }
            }
        }
    }

    private void readFromConsole(int n, InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            while (true) {
                String s = reader.readLine().trim();
                if (!s.matches("^send to:\\d+ msg:\\d+$")) {
                    System.err.println("Wrong command");
                    continue;
                }
                String[] command = s.replaceAll("[^\\d\\s]", "").trim().split(" ");
                SocketAddress address = map.get(Integer.parseInt(command[0]));
                int msg = Integer.parseInt(command[1]);
                new Thread(() -> {
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    try (SocketChannel socket = SocketChannel.open(address)) {
                        socket.configureBlocking(false);
                        buffer.putInt(n);
                        buffer.putInt(msg);
                        buffer.putLong(time.incrementAndGet());
                        buffer.flip();
                        socket.write(buffer);
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception ignored) {}
    }
}