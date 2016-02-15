package com.biiuse.motivewave;

public class InsufficientDataException extends DataException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1278544468263010231L;

	public InsufficientDataException() {
		super("Insufficient hirtorical data available");
	}
}
