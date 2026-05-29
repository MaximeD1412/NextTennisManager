// Long-lived sidecar: reads length-delimited PhysicsBatchRequest messages from
// stdin, writes length-delimited PhysicsBatchResponse messages to stdout.
// Framing: varint(byte_length) followed by the protobuf-encoded message.

use std::io::{self, BufWriter, Read, Write};

use physics_core::generated::{PhysicsBatchRequest, PhysicsBatchResponse};
use physics_core::physics::simulate_batch;
use prost::Message;

fn main() {
    let stdin = io::stdin();
    let stdout = io::stdout();
    let mut reader = stdin.lock();
    let mut writer = BufWriter::new(stdout.lock());

    loop {
        let len = match read_varint(&mut reader) {
            Ok(n) => n as usize,
            Err(e) if e.kind() == io::ErrorKind::UnexpectedEof => break,
            Err(e) => {
                eprintln!("sidecar: read error: {e}");
                std::process::exit(1);
            }
        };

        let mut buf = vec![0u8; len];
        if let Err(e) = reader.read_exact(&mut buf) {
            eprintln!("sidecar: read body error: {e}");
            std::process::exit(1);
        }

        let req = match PhysicsBatchRequest::decode(buf.as_slice()) {
            Ok(r) => r,
            Err(e) => {
                eprintln!("sidecar: decode error: {e}");
                std::process::exit(1);
            }
        };

        let env = req.env.unwrap_or_default();
        let results = simulate_batch(env, &req.shots, req.seed);
        let response = PhysicsBatchResponse { results };
        let encoded = response.encode_to_vec();

        if let Err(e) = write_varint(&mut writer, encoded.len() as u64) {
            eprintln!("sidecar: write length error: {e}");
            std::process::exit(1);
        }
        if let Err(e) = writer.write_all(&encoded) {
            eprintln!("sidecar: write body error: {e}");
            std::process::exit(1);
        }
        if let Err(e) = writer.flush() {
            eprintln!("sidecar: flush error: {e}");
            std::process::exit(1);
        }
    }
}

fn read_varint<R: Read>(reader: &mut R) -> io::Result<u64> {
    let mut result: u64 = 0;
    let mut shift = 0u32;
    loop {
        let mut byte = [0u8; 1];
        reader.read_exact(&mut byte)?;
        let b = byte[0] as u64;
        result |= (b & 0x7F) << shift;
        if b & 0x80 == 0 {
            return Ok(result);
        }
        shift += 7;
        if shift >= 64 {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "varint overflow"));
        }
    }
}

fn write_varint<W: Write>(writer: &mut W, mut value: u64) -> io::Result<()> {
    loop {
        let mut byte = (value & 0x7F) as u8;
        value >>= 7;
        if value != 0 {
            byte |= 0x80;
        }
        writer.write_all(&[byte])?;
        if value == 0 {
            break;
        }
    }
    Ok(())
}
