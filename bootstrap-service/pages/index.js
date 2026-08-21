export default function BootstrapRelayStatus() {
  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 720, margin: '72px auto', padding: '0 24px' }}>
      <h1>Simple Voice Chat Bootstrap Relay</h1>
      <p>This service accepts short-lived pairing requests only. It does not expose session secrets in the browser.</p>
      <p>Health checks are available to configured server operators at <code>/api/health</code>.</p>
    </main>
  );
}
