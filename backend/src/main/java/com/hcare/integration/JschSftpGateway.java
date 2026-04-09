package com.hcare.integration;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * JSch-backed SFTP gateway used for vendor file delivery (e.g. Netsmart, Tellus).
 *
 * <p>Uses the community-maintained {@code com.github.mwiede:jsch} fork — the original
 * {@code com.jcraft:jsch} has been unmaintained since 2018.
 *
 * <p>Strict host key checking is disabled ({@code StrictHostKeyChecking=no}) because all
 * target hosts are known, pre-approved vendor endpoints. Connection and channel are
 * always closed in the finally block regardless of outcome.
 */
@Component
public class JschSftpGateway implements SftpGateway {

  private static final Logger log = LoggerFactory.getLogger(JschSftpGateway.class);
  private static final int CONNECT_TIMEOUT_MS = 30_000;

  @Override
  public void upload(
      String host, int port, String user, String privateKeyRef, String remotePath, byte[] content)
      throws Exception {

    JSch jsch = new JSch();
    jsch.addIdentity(privateKeyRef);

    Session session = jsch.getSession(user, host, port);
    session.setConfig("StrictHostKeyChecking", "no");
    // TODO: upgrade to fingerprint pinning before first production deployment — MITM risk

    ChannelSftp channel = null;
    try {
      session.connect(CONNECT_TIMEOUT_MS);
      log.debug("SFTP session connected to {}@{}", user, host);

      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(CONNECT_TIMEOUT_MS);

      channel.put(new ByteArrayInputStream(content), remotePath);
      log.info("SFTP upload complete: {}@{}:{} ({} bytes)", user, host, remotePath, content.length);
    } finally {
      if (channel != null && channel.isConnected()) {
        channel.disconnect();
      }
      if (session.isConnected()) {
        session.disconnect();
      }
    }
  }
}
