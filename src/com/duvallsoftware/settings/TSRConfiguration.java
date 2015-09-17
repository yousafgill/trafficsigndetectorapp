package com.duvallsoftware.settings;

public class TSRConfiguration {
	public static final int NUMBER_WARNING_SIGNS = 6; 
	public static final int NUMBER_FORBIDDEN_SIGNS = 15;
	public static final int NUMBER_OBLIGATORY_SIGNS = 6;
	public static final int NUMBER_INFORMATION_SIGNS = 2;	
		
	public static final String warning_sign_id_array[] = { "A1a", "A1b", "B1", "B8", "B9a", "B9b" };	
	public static final String forbidden_sign_id_array[] = { "B2", "C1", "C2", "C11a", "C11b", "C13_40", "C13_50", "C13_60", "C13_70", "C13_80", "C13_90", "C13_100", "C13_120", "C14a", "C14b" };
	public static final String obligatory_sign_id_array[] = { "D1a", "D1c", "D3a", "D4", "D8_40", "D8_50" };
	public static final String information_sign_id_array[] = { "B6", "H7" };
	
	public static final int NO_SIGN = 0;
	public static final int WARNING_SIGN = 1;
	public static final int FORBIDDEN_SIGN = 2;
	public static final int OBLIGATORY_SIGN = 3;
	public static final int INFORMATION_SIGN = 4;
}
