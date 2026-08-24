# Анализ структуры UDP-пакета на сервере (PumpkinVoice)

Основано на файле `server.rs`:

```rust
fn handle_packet(&self, server: &Server, data: &[u8], src: std::net::SocketAddr) {
    let config = crate::config::CONFIG.read().unwrap();
    if data.len() < 17 { return; }
    if data[0] != 0xFF { return; }
    
    let mut uuid_bytes = [0u8; 16];
    uuid_bytes.copy_from_slice(&data[1..17]);
    let player_id = Uuid::from_bytes(uuid_bytes);
    
    // ...
    let mut payload_buf = &data[17..];
    let payload_bytes = payload_buf.get_byte_array();
    
    match player_state.secret.decrypt(&payload_bytes) {
        Ok(decrypted) => {
            let packet_type = decrypted[0];
            let mut packet_data = &decrypted[1..];
            // ...
```

## Критические отличия от текущей реализации адаптера

1. **Magic Byte**: Сервер ожидает, что самый первый байт UDP-датаграммы будет `0xFF`. Наша текущая реализация начинает датаграмму сразу с UUID.
2. **Формат Payload**: Сервер извлекает зашифрованную часть, используя `get_byte_array()`, что означает чтение префикса длины (обычно VarInt или 4-байтовый int) перед самими байтами. Наша текущая реализация просто дописывает `nonce + ciphertext + tag` в конец датаграммы.

### Структура входящего пакета на сервере:
1. `magic_byte` (1 байт) = `0xFF`
2. `player_uuid` (16 байт)
3. `encrypted_payload` (byte_array = length + bytes)

Где `encrypted_payload` bytes это, вероятно, `nonce` (12 байт) + `ciphertext` + `tag` (16 байт), или как-то иначе упаковано. Нужно посмотреть, как работает `get_byte_array()` и `decrypt()`.

## Формат отправки (от сервера к клиенту)

В `crypto.rs` сервер отправляет пакеты так:
```rust
let mut final_buf = BytesMut::new();
final_buf.put_u8(0xFF);
final_buf.put_varint(encrypted.len() as i32);
final_buf.put_slice(&encrypted);
socket.send_to(&final_buf, target)
```

Это означает, что **каждый** пакет (как входящий, так и исходящий) имеет структуру:
1. `0xFF` (magic byte)
2. `length` (VarInt) - *для клиента к серверу здесь вместо VarInt передается 16-байтовый UUID, но нужно проверить, как работает клиент*
3. `encrypted_payload` (nonce + ciphertext + tag)

Однако, в `server.rs` чтение пакета *от клиента* выглядит так:
```rust
if data[0] != 0xFF { return; }
let mut uuid_bytes = [0u8; 16];
uuid_bytes.copy_from_slice(&data[1..17]);
// ...
let mut payload_buf = &data[17..];
let payload_bytes = payload_buf.get_byte_array(); // get_byte_array читает VarInt длину, затем байты
```

Это значит, что клиент отправляет:
1. `0xFF` (1 байт)
2. `player_uuid` (16 байт)
3. `length` (VarInt, длина зашифрованного payload)
4. `encrypted_payload` (nonce + ciphertext + tag)

А сервер отвечает:
1. `0xFF` (1 байт)
2. `length` (VarInt, длина зашифрованного payload)
3. `encrypted_payload` (nonce + ciphertext + tag)

Наш адаптер сейчас не использует `0xFF`, не использует VarInt для длины, и ожидает UUID в ответах сервера (что неверно, сервер отвечает без UUID).
