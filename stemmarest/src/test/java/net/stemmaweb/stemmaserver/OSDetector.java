package net.stemmaweb.stemmaserver;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class OSDetector
{
	public static boolean isWin()
	{
		String OS = System.getProperty("os.name").toLowerCase();
		return (OS.indexOf("win") >= 0);
	}
}