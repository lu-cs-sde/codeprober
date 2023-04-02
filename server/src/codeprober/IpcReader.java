package codeprober;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public abstract class IpcReader {

	private enum IpcReaderState {
		NEUTRAL, READING_MESSAGE_LENGTH, READING_MESSAGE_CONTENTS, //
		READING_GARBAGE,
	}

	private final InputStream src;
	private ByteArrayOutputStream baos = new ByteArrayOutputStream();
	private int messageLength = 0;
	private IpcReaderState state = IpcReaderState.NEUTRAL;
	private boolean hasClosedSrc;

	public IpcReader(InputStream src) {
		super();
		this.src = src;
	}

	public void setSrcWasClosed() {
		this.hasClosedSrc = true;
	}

	private void onMessage(byte[] data) {
//		System.out.println("Response message: " + Arrays.toString(data));
//		System.out.println("asStr: " + new String(data, 0, data.length, StandardCharsets.UTF_8));
		onMessage(new String(data, 0, data.length, StandardCharsets.UTF_8));
	}

	protected abstract void onMessage(String data);

	private void onGarbageEnd() {
//		final byte[] data = baos.toByteArray();
		baos.reset();
//		System.out.println("♻️: " + new String(data, 0, data.length, StandardCharsets.UTF_8));
	}

	protected void handleByte(byte b) {
//		System.out.println("ipc << " + b);
		switch (state) {
		case NEUTRAL: {
			if (b == '\n') {
				state = IpcReaderState.NEUTRAL;
			} else if (b == (byte) '<') {
				messageLength = 0;
				state = IpcReaderState.READING_MESSAGE_LENGTH;
				return;
			} else {
				baos.write(b);
				state = IpcReaderState.READING_GARBAGE;
			}
			break;
		}
		case READING_GARBAGE: {
			if (b == '\n') {
				onGarbageEnd();
				state = IpcReaderState.NEUTRAL;
			} else {
				baos.write(b);
			}
			break;
		}
		case READING_MESSAGE_LENGTH: {
			if (b == '\n') {
				onGarbageEnd();
				state = IpcReaderState.NEUTRAL;
			} else {
				baos.write(b);
				if ((b >= '0') && (b <= '9')) {
					messageLength = (messageLength * 10) + (b - '0');
				} else if (b == '>') {
					if (messageLength == 0) {
						state = IpcReaderState.READING_GARBAGE;
					} else {
						baos.reset();
						state = IpcReaderState.READING_MESSAGE_CONTENTS;
					}
				} else {
					baos.write(b);
					state = IpcReaderState.READING_GARBAGE;
				}

			}
			break;
		}
		case READING_MESSAGE_CONTENTS: {
			baos.write(b);
			--messageLength;
			if (messageLength == 0) {
				onMessage(baos.toByteArray());
				baos.reset();
				state = IpcReaderState.NEUTRAL;
			}
			break;
		}
		}
	}

	public void runForever() {
		final byte[] buf = new byte[512];

		try {
			while (!hasClosedSrc) {
				final int read = src.read(buf);

				for (int i = 0; i < read; i++) {
					handleByte(buf[i]);
				}
			}
		} catch (IOException e) {
			if (!hasClosedSrc) {
				System.err.println("Worker process died");
				e.printStackTrace();
			}
		}
	}
}
