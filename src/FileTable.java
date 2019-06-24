import java.util.Vector;

/**
 * @author Thuan Tran
 *         CSS 430 Final Project
 *         May 31th, 2017
 */
public class FileTable
{
    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable(Directory directory)
    { // constructor
        table = new Vector();     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    /**
     * This method will allocate a table Entry (System wide) for a file, with different mod
     *
     * @param filename The name of the file
     * @param mode     The mode to access the file
     * @return The table entry, or null if there is no file and the system want to read it
     */
    public synchronized FileTableEntry falloc(String filename, String mode)
    {
        Inode theINode = null;

        short iNumber;
        while (true)
        {
            // If it is in the directory, then we need to get the information from the directory
            // The iNumber that correspond to the DIrectory is 0, so that was easy
            if (filename.equals("/"))
            {
                iNumber = 0;
            } else
            {
                // Else we need to look for a file
                iNumber = this.dir.namei(filename);
            }
            // If we could not find the corresponding file but the system want to read it
            if (iNumber < 0)
            {
                if (mode.equals("r"))
                {
                    return null;
                }

            } else
            {   // Get the new inode given the number of it
                theINode = new Inode(iNumber);
                if (theINode.equals("r"))
                {
                    // If they are not in a usable condition, then need to wait for change
                    if (theINode.flag != 0 && theINode.flag != 1)
                    {
                        try
                        {
                            wait();
                        } catch (InterruptedException exception)
                        {

                        }
                        continue;
                    }
                    // Set the flag to being used
                    theINode.flag = 1;
                    break;
                }
                // If they are in different mode (writing or appending),
                // and the flag does not indicate a usable condition
                if (theINode.flag != 0 && theINode.flag != 3)
                {
                    if (theINode.flag == 1 || theINode.flag == 2)
                    {
                        // Update the node to a written condition or append
                        theINode.flag = (short) 4;
                        theINode.toDisk(iNumber);
                    }
                    try
                    {
                        // Wait for completion
                        this.wait();
                    } catch (InterruptedException exception)
                    {

                    }
                    continue;
                }
                // Set the mode back to being Read

                theINode.flag = 2;
                break;
            }
            // Allocate a new file in the directory and set the inode to 2 indicating being read
            iNumber = dir.ialloc(filename);
            theINode = new Inode();
            theINode.flag = 2;
            break;
        }
        // Now there is one more File Table Entry poitning to the node
        theINode.count++;
        // Write it back to the disk
        theINode.toDisk(iNumber);
        FileTableEntry theEntry = new FileTableEntry(theINode, iNumber, mode);
        // Update the table
        table.addElement(theEntry);
        return theEntry;

    }

    /**
     * This method free a file table entry in the File Table. It will make
     * sure to write back the data to the disk
     *
     * @param e the FileTableEntry that we need to free
     * @return a boolean value: True if we found the element and freed it, false if the the parameter is
     * invalid or we could not find it
     */
    public synchronized boolean ffree(FileTableEntry e)
    {


        // If we successfully remove this file table entry
        // Then we need to decrement the count of its inode
        if (this.table.removeElement(e))
        {
            Inode theCurrentNode = e.inode;
            theCurrentNode.count--;
            int flag = e.inode.flag;
            // The node is being read or nothing much so reset to default condition
            // In this condition, the iNode can be access
            if (flag == 1 || flag == 2)
            {
                e.inode.flag = 0;
            }
            // Reset the node to being in a special condition so that future access take notice
            if (flag == 4 || flag == 5)
            {
                e.inode.flag = 3;
            }
            theCurrentNode.toDisk(e.iNumber);
            // Notify the waiting other File Table Entry that the iNode has changed
            // So they might access it
            notify();
            return true;
        }
        return false;

    }

    /**
     * This method check if the table is empty
     *
     * @return True if the table is empty
     */
    public synchronized boolean fempty()
    {
        return table.isEmpty();  // return if table is empty
    }                            // should be called before starting a format

}