package com.amazonaws.services.lambda;

import java.util.HashMap;

import junit.framework.TestCase;

public class TestEventEngine extends TestCase {
	public void testV1_0() throws Exception {
		EventEngine ee = new EventEngine("/EventEngineGraph-v1.0.json");
		ee.doExecute(new HashMap<String, String>() {
			{
				put("Arg1", "Ian");
				put("Arg2", "Test");
				put("abc", "123");
				put("do-re-me", "abc");
			}
		});
	}
	public void testV1_1() throws Exception {
		EventEngine ee = new EventEngine("/EventEngineGraph-v1.1.json");
		ee.doExecute(new HashMap<String, String>() {
			{
				put("Arg1", "Ian");
				put("Arg2", "Test");
				put("abc", "123");
			}
		});
	}
}
