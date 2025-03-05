package codeprober.protocol;

import java.io.DataInputStream;
import java.io.IOException;

public interface BinaryInputStream {
	int readInt() throws IOException;
	long readLong() throws IOException;
	boolean readBoolean() throws IOException;
	String readUTF() throws IOException;


	public static class DataInputStreamWrapper implements BinaryInputStream {

		private final DataInputStream dis;

		public DataInputStreamWrapper(DataInputStream dos) {
			this.dis = dos;
		}

		@Override
		public int readInt() throws IOException {
			return dis.readInt();
		}

		@Override
		public long readLong() throws IOException {
			return dis.readLong();
		}

		@Override
		public boolean readBoolean() throws IOException {
			return dis.readBoolean();
		}

		@Override
		public String readUTF() throws IOException {
			return dis.readUTF();
		}

	}
}
