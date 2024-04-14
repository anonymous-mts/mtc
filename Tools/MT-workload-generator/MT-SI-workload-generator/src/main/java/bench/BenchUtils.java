package bench;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.ZipfDistribution;

import main.Config;
import main.Config.KeyDist;

public class BenchUtils {

	// string generator
	public static final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public static final String lower = upper.toLowerCase();
	public static final String digits = "0123456789";
	public static final String alphanum = upper + lower + digits;

	public static String getRandomValue() {
		String res = getRandomValue(Config.get().VALUE_LENGTH);
		return res;
	}

	public static String getRandomValue(int length) {
		int size = alphanum.length();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			int pos = ThreadLocalRandom.current().nextInt(size);
			sb.append(alphanum.charAt(pos));
		}
		return sb.toString();
	}

	public static String shuffleString(String input) {
		List<Character> characters = new ArrayList<Character>();
		for (char c : input.toCharArray()) {
			characters.add(c);
		}
		StringBuilder output = new StringBuilder(input.length());
		while (characters.size() != 0) {
			int randPicker = (int) (Math.random() * characters.size());
			output.append(characters.remove(randPicker));
		}
		return output.toString();
	}

	// integer generator
	public static int getRandomInt(int from, int to) {
		assert to > from;
		int rand = ThreadLocalRandom.current().nextInt(to - from);
		return from + rand;
	}

	public static String MakeTimeStamp() {
		return new SimpleDateFormat("dd-MM-yyyy").format(new Date());
	}

	public static boolean getBitMapAt(byte[] b, int p) {
		return (b[p / 8] & (1 << (p % 8))) != 0;
	}

	public static void setBitMapAt(byte[] b, int p) {
		b[p / 8] |= 1 << (p % 8);
	}

	public static void clearBitMapAt(byte[] b, int p) {
		b[p / 8] &= ~(1 << (p % 8));
	}

	public static ArrayList<Integer> getKeySeq(int keyNum, int num, KeyDist keyDist) {
		ArrayList<Integer> keySequence = new ArrayList<>();

		switch (keyDist) {
			case UNIFORM:
				generateUniformKeySeq(keyNum, num, keySequence);
				break;
			case ZIPF:
				generateZipfKeySeq(keyNum, num, keySequence);
				break;
			case HOTSPOT:
				generateHotspotKeySeq(keyNum, num, keySequence);
				break;
			case EXPONENTIAL:
				generateExponentialKeySeq(keyNum, num, keySequence);
				break;
			default:
				throw new IllegalArgumentException("Unsupported key distribution type: " + keyDist);
		}

		return keySequence;
	}

	private static void generateUniformKeySeq(int keyNum, int num, ArrayList<Integer> keySequence) {
		Random random = new Random();

		for (int i = 0; i < num; i++) {
			int randomIndex = random.nextInt(keyNum);
			keySequence.add(randomIndex);
		}
	}

	private static void generateZipfKeySeq(int keyNum, int num, ArrayList<Integer> keySequence) {
		double exponent = 2.0;
		ZipfDistribution zipfDistribution = new ZipfDistribution(keyNum, exponent);

		for (int i = 0; i < num; i++) {
			int keyIndex = zipfDistribution.sample() - 1; // Adjust for 0-based indexing
			keySequence.add(keyIndex);
		}
	}

	private static void generateHotspotKeySeq(int keyNum, int num, ArrayList<Integer> keySequence) {
		Random random = new Random();
		int hotspotIndex = random.nextInt(keyNum);
		double hotspotProbability = 0.2;

		for (int i = 0; i < num; i++) {
			if (random.nextDouble() < hotspotProbability) {
				keySequence.add(hotspotIndex);
			} else {
				int randomIndex = random.nextInt(keyNum);
				keySequence.add(randomIndex);
			}
		}
	}

	private static void generateExponentialKeySeq(int keyNum, int num, ArrayList<Integer> keySequence) {
		double lambda = 0.1;
		Random random = new Random();

		for (int i = 0; i < num; i++) {
			double randomValue = random.nextDouble();
			int keyIndex = (int) (-Math.log(1 - randomValue) / lambda);
			keyIndex = Math.min(keyIndex, keyNum - 1); // Ensure the index is within bounds
			keySequence.add(keyIndex);
		}
	}
}
