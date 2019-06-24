/**
 * @author Thuan Tran
 *         CSS 430 Program 5
 *         Date: May 31th, 2017
 */


public class Directory
{
    private static int maxChars = 30; // max characters of each file name

    // Directory entries
    private int fsizes[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.


    /**
     * Constructor for the Directory
     *
     * @param maxInumber max number of iNodes (Files) to be created
     */


    public Directory(int maxInumber)
    { // directory constructor
        fsizes = new int[maxInumber];
        // maxInumber = max files
        for (int i = 0; i < maxInumber; i++)
        {
            fsizes[i] = 0;                 // all file size initialized to 0
        }
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsizes[0] = root.length();        // fsize[0] is the size of "/".
        root.getChars(0, fsizes[0], fnames[0], 0); // fnames[0] includes "/"
    }


    /**
     * Populate the directory with a byte array
     *
     * @param data the byte array that have the information of the directory
     * @return a 1 if it succeed, or a -1 if it fails
     */

    public int bytes2directory(byte data[])
    {

        if (data == null || data.length == 0)
        {
            // FAILURE CODE
            return -1;
        }
        // https://stackoverflow.com/questions/11438794/is-the-size-of-c-int-2-bytes-or-4-bytes
        // Explanation why I increment 4 because int is represent by 4 byte
        // The offset is incremented 4 each time because
        int difference = 0;
        for (int i = 0; i < fsizes.length; i++)
        {
            fsizes[i] = SysLib.bytes2int(data, difference);
            difference += 4;

        }
        // maxChar * 2 = 60 bytes, maximum the file byte. So moving past to the next file
        // Write a string that is at most 60 bytes long by moving the offset 60 bytes
        // Get the character into the location at fsizes using 60 bytes (30 chars)
        for (int i = 0; i < fsizes.length; i++)
        {
            String name = new String(data, difference, maxChars * 2);
            name.getChars(0, fsizes[i], fnames[i], 0);
            difference += maxChars * 2;
        }
        // Success code 
        return 1;


    }


    /**
     * Translate the directory information into a byte array
     *
     * @return a byte array that represent the directory
     */


    public byte[] directory2bytes()
    {


        // The directory have fnames.length file. Translating them to bytes is time 60 = maxChars *2
        // Also need to account for the transition from integer to byte as well
        // addition because the directory contain both the fsizes and fnames variable
        byte[] dir = new byte[fsizes.length * 4 + this.fnames.length * maxChars * 2];
        int differnce = 0;

        // Done with the file size
        for (int i = 0; i < this.fsizes.length; i++)
        {
            // Write into the byte array dir the data from fsizes[i] at location difference
            SysLib.int2bytes(this.fsizes[i], dir, differnce);
            differnce += 4;
        }
        // Move on to file name variable
        for (int i = 0; i < this.fnames.length; i++)
        {
            String name = new String(this.fnames[i], 0, this.fsizes[i]);
            byte[] data = name.getBytes();
            System.arraycopy(data, 0, dir, differnce, data.length);
            differnce += maxChars * 2;
        }

        return dir;

    }


    /**
     * Allocate an empty spot on the directory and return the iNode number to attach with it
     *
     * @param filename The file to be put
     * @return the iNode number that correspond to the file
     */


    public short ialloc(String filename)
    {

        for (int i = 0; i < fsizes.length; i++)
        {
            if (fsizes[i] == 0)
            {
                // Account for cases where file name is longer or file name is way shorter
                // So the charAt does not access invalid position
                int min = Math.min(filename.length(), maxChars);
                for (int j = 0; j < min; j++)
                {
                    fnames[i][j] = filename.charAt(j);
                }
                fsizes[i] = min;
                return (short) i;
            }
        }
        return -1;

    }


    /**
     * Free the file at the passed in node number
     *
     * @param iNumber iNode number
     * @return a boolean variable if we successfully free the file or not. True is yes, we freed the file. False otherwise
     */

    public boolean ifree(short iNumber)
    {


        if (iNumber < 0 || fsizes[iNumber] <= 0)
        {
            return false;
        }
        for (int i = 0; i < maxChars; i++)
        {
            // Set to default by removing the file name
            fnames[iNumber][i] = 0;
        }
        fsizes[iNumber] = 0;
        return true;
    }


    /**
     * Get the node number of the passed in file name
     *
     * @param filename The file name that we need to search
     * @return the inode number that correspond to this file name
     */


    public short namei(String filename)
    {

        for (int i = 0; i < fsizes.length; i++)
        {
            // Okay,they are the same size. It has potential
            if (fsizes[i] == filename.length())
            {
                // Get the file name
                StringBuilder theName = new StringBuilder();
                for (int j = 0; j < fsizes[i]; j++)
                {
                    theName.append(fnames[i][j]);

                }
                if (theName.equals(filename))
                {
                    return (short) i;
                }
            }
        }
        // Invalid Data
        return -1;


    }
}




