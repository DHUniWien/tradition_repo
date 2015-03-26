package net.stemmaweb.stemmaserver;

public class OSDetector
{
	public static boolean isWin()
	{
		String OS = System.getProperty("os.name").toLowerCase();
		return (OS.indexOf("win") >= 0);
	}
}