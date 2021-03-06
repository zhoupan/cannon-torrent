package utils;

import java.nio.ByteBuffer;
import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JTable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;

import peers.*;

/**
 * @author Matt
 * 
 */
/**
 * @author Matt
 * 
 */
public class Manager {

	public static byte[] peer_id = new byte[20];
	public static byte[] info_hash = new byte[20];
	public static TorrentInfo torrent_info = null;
	public static ConcurrentLinkedQueue<Block> q = null;
	public static AtomicIntegerArray have_piece = null;
	public static File file = null;
	public static int port = 0;
	public static int interval = 0;
	public static int minInterval = 0;
	public static int numPieces = 0;
	public static int numLeft = 0;
	public static int numBlocks = 0;
	public static int blocksPerPiece = 0;
	public static int blocksInLastPiece = 0;
	public static int leftoverBytes = 0;
	public static boolean peersReady = false;
	public static boolean piecesReady = false;
	public static boolean fileDone = false;
	public static ReentrantLock fileLock = new ReentrantLock();
	public static ArrayList<Peer> activePeerList = null;
	public static ArrayList<Peer> unchokedPeers = null;
	public static ArrayList<Peer> wantUnchokePeers = null;
	public static int downloaded = 0;
	public static int uploaded = 0;
	public static AtomicInteger numUnchoked = new AtomicInteger(0);

	public static final int block_length = 16384;
	public static final String STARTED = "started";
	public static final String COMPLETED = "completed";
	public static final String STOPPED = "stopped";
	public static final String EMPTY = "";

	/**
	 * GUI Globals
	 */
	public static JProgressBar progress = null;
	public static JLabel piecesLabel = null;
	public static JTable peerTable = null;

	public static ArrayList<Peer> peerList_ = null;

	/**
	 * empty constructor
	 */
	public Manager() {
	}

	/**
	 * Constructor
	 * 
	 * @param torrentFile
	 *            name of torrent file we are using
	 * @param fileName
	 *            the name of the file we are saving data to
	 */
	public Manager(String torrentFile, String fileName) {
		setInfo(torrentFile, fileName);

		leftoverBytes = torrent_info.file_length % block_length;

		numPieces = leftoverBytes == 0 ? torrent_info.file_length
				/ torrent_info.piece_length : torrent_info.file_length
				/ torrent_info.piece_length + 1;
		blocksPerPiece = torrent_info.piece_length / block_length;

		numBlocks = (int) Math.ceil(torrent_info.file_length / block_length);

		have_piece = new AtomicIntegerArray(numPieces);

		activePeerList = new ArrayList<Peer>();
		unchokedPeers = new ArrayList<Peer>();
		wantUnchokePeers = new ArrayList<Peer>();

		// set up the reference queue
		q = new ConcurrentLinkedQueue<Block>();
		for (int total = 0, j = 0; j < numPieces; j++) {
			for (int k = 0; k < blocksPerPiece; k++, total++) {
				byte[] data = null;
				if (j == numPieces - 1 && total == numBlocks) {
					data = new byte[leftoverBytes];
					Block b = new Block(j, k, data);
					q.add(b);
					blocksInLastPiece = b.getBlock() + 1;
					break;
				} else {
					data = new byte[block_length];
				}
				Block b = new Block(j, k, data);
				q.add(b);
			}
		}
	}

	/**
	 * starts the timers to start the piecechecker, trackercontact and choker
	 * threads
	 */
	public static void setTimers() {
		Timer t1 = new Timer();
		PieceChecker checker = new PieceChecker();
		t1.scheduleAtFixedRate(checker, 0, 1000);

		Timer t2 = new Timer();
		TrackerContact contact = new TrackerContact(0);
		t2.schedule(contact, interval * 1000, interval * 1000);

		Timer t3 = new Timer();
		Choker choke = new Choker();
		t3.scheduleAtFixedRate(choke, 30000, 30000);
		return;
	}

	/**
	 * 
	 * @returns when done with starting the download threads
	 */
	public boolean download() {
		for (Peer peer : peerList_) {
			registerPeer(peer);
			DownloadThread p = new DownloadThread(peer);
			Thread a = new Thread(p);
			a.start();
		}
		return false;
	}

	/**
	 * restarts the peers
	 */
	public static void restart() {
		for (Peer peer : peerList_) {
			if (peer.socket_ == null) {
				// System.out.println("Restarting peer " + peer.peer_id_);
				DownloadThread p = new DownloadThread(peer);
				Thread a = new Thread(p);
				a.start();
			}
		}
	}

	/**
	 * 
	 * @param torrentFile
	 *            the torrent file name we are reading
	 * @param fileName
	 *            the file name that we are downloading to
	 */
	public static void setInfo(String torrentFile, String fileName) {
		try {
			torrent_info = new TorrentInfo(Helpers.readTorrent(torrentFile));
			torrent_info.info_hash.get(info_hash, 0, info_hash.length);
			file = new File(fileName);
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("Torrent file could not be loaded.");
			System.exit(1);
		}
	}

	/**
	 * 
	 * @return gets our peer id
	 */
	public static byte[] getPeerId() {
		return peer_id;
	}

	/**
	 * 
	 * @param peer_id
	 *            sets our peer id
	 */

	public static void setPeerId(byte[] peer_id) {
		Manager.peer_id = peer_id;
	}

	/**
	 * 
	 * @return gets gets the info hash
	 */
	public static byte[] getInfoHash() {
		return info_hash;
	}

	/**
	 * 
	 * @param info_hash
	 *            sets the info hash
	 */
	public static void setInfoHash(byte[] info_hash) {
		Manager.info_hash = info_hash;
	}

	/**
	 * 
	 * @return gets the torrent info
	 */
	public static TorrentInfo getTorrentInfo() {
		return torrent_info;
	}

	/**
	 * 
	 * @param torrent_info
	 *            sets the torrent info
	 */
	public static void setTorrentInfo(TorrentInfo torrent_info) {
		Manager.torrent_info = torrent_info;
	}

	/**
	 * 
	 * @return gets the file we are writing to
	 */
	public static File getFile() {
		return file;
	}

	/**
	 * 
	 * @param file
	 *            sets the file we are using
	 */
	public static void setFile(File file) {
		Manager.file = file;
	}

	/**
	 * 
	 * @return the port we are using
	 */
	public static int getPort() {
		return port;
	}

	/**
	 * 
	 * @param port
	 *            sets the port we are using
	 */
	public static void setPort(int port) {
		Manager.port = port;
	}

	/**
	 * 
	 * @return returns an object[][] of the peer list.
	 */
	public static Object[][] getPeerList() {
		Object[][] all = new Object[peerList_.size()][3];
		for (int i = 0; i < peerList_.size(); i++) {
			Peer peer = peerList_.get(i);
			Object[] list = { peer.peer_id_, peer.ip_, peer.port_,
					peer.downloaded.get(), peer.uploaded.get() };
			all[i] = list;
		}
		return all;
	}

	/**
	 * 
	 * @return returns the number of pieces
	 */
	public static int getNumPieces() {
		return numPieces;
	}

	/**
	 * 
	 * @param numPieces
	 *            sets the number of pieces
	 */
	public static void setNumPieces(int numPieces) {
		Manager.numPieces = numPieces;
	}

	/**
	 * Downloads the pieces from the peer and stores them in the appropriate
	 * file
	 * 
	 * @param peer
	 *            the peer object to be downloading from
	 */

	/**
	 * Generates our random PeerID
	 */
	public static void setPeerId() {
		Random ran = new Random();
		int rand_id = ran.nextInt(5555555 - 1000000 + 1) + 1000000;
		String peer_id_string = "GROUP4AREL33t" + rand_id;
		peer_id = peer_id_string.getBytes();
	}

	/**
	 * Construct the url for tracker querying
	 * 
	 * @param port
	 *            port to be used
	 * @param uploaded
	 *            amount uploaded
	 * @param downloaded
	 *            amount downloaded
	 * @param left
	 *            amount left
	 * @param event
	 *            event type
	 * @return String to be sent as query
	 */
	public static String constructQuery(int port, int uploaded, int downloaded,
			int left, String event) {
		String url_string = "";
		try {
			String escaped_hash = Helpers.toURLHex(info_hash);
			String escaped_id = Helpers.toURLHex(peer_id);
			InetAddress thisIp = InetAddress.getLocalHost();
			url_string = torrent_info.announce_url.toString() + "?port=" + port
					+ "&peer_id=" + escaped_id + "&info_hash=" + escaped_hash
					+ "&uploaded=" + uploaded + "&downloaded=" + downloaded
					+ "&left=" + left;

			if (event.length() != 0) {
				url_string += "&event=" + event;
			}

		} catch (Exception e) {
			// System.out.println(e);
		}

		return url_string;
	}

	/**
	 * queries the tracker and gets the initial list of peers
	 */
	public static void queryTracker() {
		byte[] response = null;
		int i = 0;
		for (i = 6881; i <= 6889;) {
			try {
				response = Helpers.getURL(constructQuery(i, 0, 0,
						torrent_info.file_length, "started"));
				setPort(i);
				break;
			} catch (Exception e) {
				// System.out.println("Port " + i + " failed");
				i++;
				continue;
			}
		}
		setPeerList(response);
	}

	public static final ByteBuffer intervalKey = ByteBuffer.wrap(new byte[] {
			'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public static final ByteBuffer peersKey = ByteBuffer.wrap(new byte[] { 'p',
			'e', 'e', 'r', 's' });
	public static final ByteBuffer minIntervalKey = ByteBuffer.wrap(new byte[] {
			'm', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public static final ByteBuffer downloadedKey = ByteBuffer.wrap(new byte[] {
			'd', 'o', 'w', 'n', 'l', 'o', 'a', 'd', 'e', 'd' });
	public static final ByteBuffer completeKey = ByteBuffer.wrap(new byte[] {
			'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });
	public static final ByteBuffer ipKey = ByteBuffer.wrap(new byte[] { 'i',
			'p' });
	public static final ByteBuffer peerIdKey = ByteBuffer.wrap(new byte[] {
			'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public static final ByteBuffer portKey = ByteBuffer.wrap(new byte[] { 'p',
			'o', 'r', 't' });

	/**
	 * Gets the peer list from a response from the tracker
	 * 
	 * @param response
	 *            byte array response from
	 * @return returns the array list of peers
	 */
	public static void setPeerList(byte[] response) {
		ArrayList<Peer> peerList = new ArrayList<Peer>();
		try {
			Object decodedResponse = Bencoder2.decode(response);
			// ToolKit.print(decodedResponse, 1);

			Map<ByteBuffer, Object> responseMap = (Map<ByteBuffer, Object>) decodedResponse;

			interval = (Integer) responseMap.get(intervalKey);
			if (interval > 180 * 1000) {
				interval = 180 * 1000;
			}

			minInterval = interval / 2;
			try {
				minInterval = (Integer) responseMap.get(minIntervalKey);
			} catch (Exception e) {
				// System.out.println("Min interval is not present");
			}

			ArrayList<Object> peerArray = (ArrayList<Object>) responseMap
					.get(peersKey);

			for (int i = 0; i < peerArray.size(); i++) {
				Object peer = peerArray.get(i);
				String ip_ = "";
				String peer_id_ = "";
				int port_ = 0;

				Map<ByteBuffer, Object> peerMap = (Map<ByteBuffer, Object>) peer;
				ip_ = Helpers.bufferToString((ByteBuffer) peerMap.get(ipKey));
				peer_id_ = Helpers.bufferToString((ByteBuffer) peerMap
						.get(peerIdKey));
				port_ = (Integer) peerMap.get(portKey);
				// //System.out.println(ip_ +" " + peer_id_ +" " + port_);
				Peer newPeer = new Peer(peer_id_, ip_, port_);

				if (newPeer.isValid()) {
					peerList.add(newPeer);
				}
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}

		peerList_ = peerList;
		Manager.peersReady = true;
	}

	/**
	 * @return our bitfield as a byte[] to send as a message
	 */
	public static byte[] getBitfield() {
		return BitToBoolean.convert(BitToBoolean.convert(have_piece));
	}

	/**
	 * @param p
	 *            registers a peer for our active peer list
	 */
	public static void registerPeer(Peer p) {
		activePeerList.add(p);
	}

	/**
	 * 
	 * @param added
	 *            adds the amount downloaded to the downloaded in the peer
	 */
	public static void addDownloaded(int added) {
		downloaded = downloaded + added;
	}

	/**
	 * 
	 * @param added
	 *            adds the amount uploaded to the uploaded in the peer
	 */
	public static void addUploaded(int added) {
		uploaded += added;
	}
}
