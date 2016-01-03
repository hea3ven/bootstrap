package com.hea3ven.tools.bootstrap;

public class BootstrapError extends RuntimeException {
	public BootstrapError(String msg) {
		super(msg);
	}

	public BootstrapError(String msg, Exception ex) {
		super(msg, ex);
	}
}
