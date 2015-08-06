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
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}