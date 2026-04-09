package com.hcare.integration;

/**
 * Abstraction over SFTP upload operations, enabling test doubles for integration tests.
 */
public interface SftpGateway {

  /**
   * Uploads {@code content} to {@code remotePath} on the given SFTP host.
   *
   * @param host          the SFTP hostname or IP address
   * @param user          the SSH username
   * @param privateKeyRef path to the private key file used for authentication
   * @param remotePath    the absolute remote path including filename
   * @param content       the bytes to upload
   * @throws Exception on any SFTP or network error
   */
  void upload(String host, String user, String privateKeyRef, String remotePath, byte[] content)
      throws Exception;
}
