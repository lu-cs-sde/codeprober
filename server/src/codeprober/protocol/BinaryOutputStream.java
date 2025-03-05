package codeprober.protocol;

import java.io.DataOutputStream;
import java.io.IOException;

public interface BinaryOutputStream {
	void writeInt(int v) throws IOException;

	void writeLong(long v) throws IOException;

	void writeBoolean(boolean v) throws IOException;

	void writeUTF(String v) throws IOException;

	public static class DataOutputStreamWrapper implements BinaryOutputStream {

		private final DataOutputStream dos;

		public DataOutputStreamWrapper(DataOutputStream dos) {
			this.dos = dos;
		}

		@Override
		public void writeInt(int v) throws IOException {
			this.dos.writeInt(v);
		}

		@Override
		public void writeLong(long v) throws IOException {
			this.dos.writeLong(v);
		}

		@Override
		public void writeBoolean(boolean v) throws IOException {
			this.dos.writeBoolean(v);
		}

		@Override
		public void writeUTF(String v) throws IOException {
			this.dos.writeUTF(v);
		}

	}
}
