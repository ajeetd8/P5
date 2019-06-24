
/*
 The main file system class that ties everything together and its how you declare a FileSystem
 (FileSystem fs )
 */

public class FileSystem
{
    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    public FileSystem(int diskBlocks)
    {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.totalInodes);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0)
        {
            byte[] dirData = new byte[dirSize];

            read(dirEnt, dirData);
            // Update the directory with the data that received from the buffer
            directory.bytes2directory(dirData);
        }

        close(dirEnt);

    }


    /**
     * - allocates  new file descriptor fd to this file.
     * <p>
     * - file not exist in modes: “w”, “w+” or “a” 	-> file is created
     * - file not exist in mode: “r”				-> error -> return negative number
     * <p>
     * - file descriptor 0, 1 and 2 are reserved as standard input, output, and error
     * - therefore, newly opened file must receive a new descriptor numbered 3 <= fd <= 31
     * - if calling thread’s user file descriptor table is full, return error of negative number
     * <p>
     * seek pointer -> initialized to zero -> mode: “r”, “w”, and “w+”
     * -> initialized at the end of the file -> mode: “a”
     *
     * @param filename the name of the file to open
     * @param mode      The mode to open the file
     * @return a File Table Entry that correspond to the file with the given mode
     */

    public FileTableEntry open(String filename, String mode)
    {


        // Allocate the file in the File Table
        // Check if it is in write mode or not, if it is then we need to deallocate the block at the File Table Entry
        FileTableEntry retVal = filetable.falloc(filename, mode);
        if (mode.equals("w") && !deallocAllBlocks(retVal))
        {
            return null;
        }
        return retVal;
    }


    /**
     * - reads up to buffer.length bytes from the file indicated by fd -> starting at the position currently pointed to by the seek pointer
     * <p>
     * if (bytes remaining between current seek pointer and end of file are less than buffer.length) {
     * - SysLib.read reads as many bytes as possible	-> put them to beginning of buffer
     * - increments the seek pointer by the number of bytes to have been read
     * - return: number of bytes that have been read		OR negative error value
     * }
     *
     * @param ftEnt  the FileTableEntry to read from
     * @param buffer the buffer to read the data into
     * @return an intger value indicating successful or not
     */

    public int read(FileTableEntry ftEnt, byte[] buffer)
    {

        int sizeLeftToRead = 0;
        int trackDataRead = 0;
        int size = buffer.length;
        // Could not read if the File Table Entry has mode write or append
        if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a")) return -1;
        // Check for invalid passed in parameter
        if (buffer == null)
        {
            return -1;
        }

        synchronized (ftEnt)
        {
            // Only stop when the seek pointer is still within the range
            // And the buffer still have place to read data into
            while (ftEnt.seekPtr < fsize(ftEnt) && buffer.length > 0)
            {

                // FIND BLOCK NUMBER
                int blockNum = ftEnt.inode.getBlockNumPointer(ftEnt.seekPtr);
                //
                if (blockNum != -1)
                {

                    byte[] tempRead = new byte[Disk.blockSize];
                    // Know the block location to read from, now load the data from disk
                    SysLib.rawread(blockNum, buffer);

                    // How far we go in to


                    int dataGetInto = ftEnt.seekPtr % Disk.blockSize;
                    int remainingBlocks = Disk.blockSize - dataGetInto;
                    int remaining = fsize(ftEnt) - ftEnt.seekPtr;


                    int smallerBetweenBlockandData = Math.min(remainingBlocks, size);
                    // Check to see how much left we can read versus the size remaining
                    sizeLeftToRead = Math.min(smallerBetweenBlockandData, remaining);


                    System.arraycopy(tempRead, dataGetInto, buffer, trackDataRead, sizeLeftToRead);
                    // Update the varaible to read into the byte array
                    trackDataRead += sizeLeftToRead;
                    // Update the Seek Pointer to read at new position
                    ftEnt.seekPtr += sizeLeftToRead;
                    // Update the size total.
                    size -= sizeLeftToRead;
                } else
                {
                    // Invalid block location
                    break;
                }

            }
            return trackDataRead;

        }
        // Default return value, if reached here, then no success


    }

    /**
     * This method sync the data from the directory back to the disk
     *
     * @param none
     * @return none
     */
    public void sync()
    {
        FileTableEntry tempEntry = open("/", "w");
        // Get all the information from the directory first, including all files name and size
        byte[] temp = directory.directory2bytes();
        // Open the Table Entry that correspond to the directory

        // Write back to the disk all the info from the directory
        write(tempEntry, temp);
        close(tempEntry);
        // Write back to the disk all the info
        superblock.sync();

    }


    /**
     * - closes corresponding to fd
     * - commits all file transactions o this file
     * - unregisters fd from the user file descriptor table of the calling thread’s TCB
     * - return value is 0 in success, otherwise -1
     *
     * @param ftEnt The File Table Entry that need to be closed
     * @return a boolean avarialble that indicate if we successfully close or not
     */
    boolean close(FileTableEntry ftEnt)
    {
        // Cant close an empty variable
        if (ftEnt == null)
        {
            return false;
        }
        synchronized (ftEnt)
        {
            ftEnt.count--;
            // This mean that no others threads are using it so we can remove it from the file
            // table
            if (ftEnt.count <= 0)
            {
                return filetable.ffree(ftEnt);
            }
            return true;
        }
    }


    /**
     * - writes the contents of buffer to the file indicated by fd	->	starting at the position indicated by the seek pointer
     * - operation might overwrite existing data in the file and/ or append to the end of the file.
     * - SysLib.write increments the seek pointer by the number of bytes to have been written
     * - return value is the number of bytes that have been written	OR negative error value
     *
     * @param ftEnt  The FileTableEntry that we want to write the data into
     * @param buffer the buffer that has the data that need to be rewritten
     */
    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        // Check for invalid passed in parameter
        if (ftEnt == null || buffer == null)
        {
            return -1;
        }

        // We can not write to a table ftEnt that is read only, so return invalid code
        if (ftEnt.mode.equals( "r"))
        {
            return -1;
        }
        synchronized (ftEnt)
        {
            int offset = 0;
            int size = buffer.length;
            // Continue writing when we still have length in the buffer
            while (size > 0)
            {
                int blockPointerPointingTo = ftEnt.inode.getBlockNumPointer(ftEnt.seekPtr);
                if (blockPointerPointingTo == -1)
                {
                    short availableFreeblock = (short) superblock.findFreeBlock();
                    int result = ftEnt.inode.updateTheBlock(ftEnt.seekPtr, availableFreeblock);
                    // Depending on the return value, we will have different ways to handle
                    // When result = -3 , it means that the indirect block is unavaible albe
                    // So we need to make use of it
                    if (result == -3)
                    {
                        // Find the nextfree block to be the indirect block of the current iNode
                        short nextFreeBlock = (short) superblock.findFreeBlock();
                        // If we could update the block we are writing too, ERRORRRRRR

                        if (!ftEnt.inode.updateTheFreeBlock(nextFreeBlock))
                        {
                            return -1;
                        }
                        // If We do not success update the block given the seek pointer
                        if (ftEnt.inode.updateTheBlock(ftEnt.seekPtr, availableFreeblock) != 0)
                        {
                            return -1;
                        }
                    }
                    // When result is 0, it means that we success finding the right position
                    if (result == 0)
                    {
                        blockPointerPointingTo = availableFreeblock;
                    }
                    // This means that the direct pointer is no good
                    if (result == -2 || result == -1)
                    {
                        return -1;
                    }
                }

                // Okay, now we need to get the data of the block the pointer is pointing to
                byte[] tempData = new byte[Disk.blockSize];
                SysLib.rawread(blockPointerPointingTo, tempData);
                // Hmm, where in the block should we point
                int position = ftEnt.seekPtr % Disk.blockSize;
                int remaining = Disk.blockSize - position;
                // Remaming position that we can write, we do not want to write that is over the file
                int availablePlace = Math.min(remaining, size);
                System.arraycopy(buffer, offset, tempData, position, availablePlace);
                SysLib.rawwrite(blockPointerPointingTo, tempData);
                // Update the seek pointer poingting to the next location
                ftEnt.seekPtr += availablePlace;
                offset += availablePlace;
                // Decrement the size meaning that we have used this much space in writing
                size -= availablePlace;
                // If we have surpase the length of the inode, then we need to change it
                if (ftEnt.seekPtr > ftEnt.inode.length)
                {
                    ftEnt.inode.length = ftEnt.seekPtr;
                }
            }
            // Update the inode
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return offset;
        }

    }


    /**
     * This method deallocate all the block that is pointed to in the passed in File Table Entry
     *
     * @param ftEnt The File Table Entry that need to be deallocate
     * @return a boolean variable that indicate success or not
     */
    private boolean deallocAllBlocks(FileTableEntry ftEnt)
    {
        // Invalid parameter
        if (ftEnt == null)
        {
            return false;
        }
        // Something is using it
        if (ftEnt.count > 0)
        {
            return false;
        }
        // Deallocate the indirect block
        byte[] data;
        int indirectStatus = ftEnt.inode.indirect;
        // if the indirect is still in use, then we need to get its data first before invalidate it
        if (indirectStatus != -1)
        {

            data = new byte[Disk.blockSize];
            SysLib.rawread(indirectStatus, data);
            // Invalidate it
            ftEnt.inode.indirect = -1;
        } else
        {
            data = null;
        }
        // Now we get the data of the indirect block
        if (data != null)
        {
            byte offset = 0;
            // Get all the block that is pointed to by the indirect block
            short blockID = SysLib.bytes2short(data, offset);
            // And make it free. LET IT GO LET IT GO CAN'T HOLD IT BACK ANYMORE
            while (blockID != -1)
            {
                superblock.addFreeBlock(blockID);
                blockID = SysLib.bytes2short(data, offset);
            }
        }

        // Since each iNode can only have 11 pointer; Free all the block that is pointed to
        for (short blockIndex = 0; blockIndex < 11; blockIndex++)
        {     if(ftEnt.inode.direct[blockIndex] == -1)
        {
            // Do not free the block if the block at that location is invalid
            continue;
        }
        else
        {
            superblock.addFreeBlock(ftEnt.inode.direct[blockIndex]);
            // Indicate that the block at this direct block is invalid
            ftEnt.inode.direct[blockIndex] = -1;
        }
        }

        ftEnt.inode.toDisk(ftEnt.iNumber);
        return true;


    }


    /**
     * - deletes the file specified by fileName
     * - all blocks used by file is freed.
     * if ( file is open ) {
     * it is not deleted
     * return -1
     * }
     * - successfully delete = 0
     *
     * @param fn the Name of the file to be deleted
     * @return a boolean variable indicating if delete success or not
     */

    public boolean delete(String fn)
    {
        // Need to get the File Table Entry that has the file
        // Then get the i number which in turn point to the block that
        // need to be delete
        // Need to make sure that the file is close and free
        FileTableEntry corresponding = open(fn, "w");
        short number = corresponding.iNumber;
        boolean closeSuccess = close(corresponding);
        boolean freeSuccess = directory.ifree(number);
        if (closeSuccess && freeSuccess)
        {
            return true;
        }
        return false;


    }


    /**
     * updates the seek pointer corresponding to fd as follows:
     * - whence == SEEK_SET(= 0), file’s seek pointer is set to offset bytes from the beginning of the file
     * - whence == SEEK_CUR(= 1), file’s seek pointer is set to its current value plus the offset, The offset can be (+) or (-)
     * - whence == SEEK_END(= 2), file’s seek pointer is set to the size of the file plus the offset, The offset can be (+) or (-)
     *
     * @param ftEnt  The File Table Entry to seek the data
     * @param offset How far into it that we want to read
     * @param whence The Mode to read
     * @return an integer indicating the location of the seek, or -1 if fails
     */


    public int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        // Invalid mode to set the pointer, can only be 1,2,3
        if (whence != 0 && whence != 1 && whence != 2)
        {
            return -1;
        }

        synchronized (ftEnt)
        {
            if (ftEnt == null) return -1;

            if (whence == SEEK_SET)
            {
                // If the mode is read at the beginning of the file, the offset need to be within a range
                if (offset <= fsize(ftEnt) && offset >=0)
                {
                    ftEnt.seekPtr = offset;

                }

            } else if (whence == SEEK_CUR)
            {
                // If the mode is reading from where the seek pointer is, the posiotn need to be
                // Within the file size and greater than 0
                if (ftEnt.seekPtr + offset <= fsize(ftEnt) && ((ftEnt.seekPtr + offset) >= 0))

                {
                    // Update the seek pointer to the next position
                    ftEnt.seekPtr += offset;
                }

            } else if (whence == SEEK_END)
            {

                // If we are trying to read from the end, then we need to make sure it is still in position
                if (fsize(ftEnt) + offset >= 0 && fsize(ftEnt) + offset <= fsize(ftEnt))
                {


                    ftEnt.seekPtr = fsize(ftEnt) + offset;
                } else
                {
                    return -1;
                }
            }

            // GEt the current FileTableEntry seek pointer
            return ftEnt.seekPtr;
        }

    }


    /**
     * files: max number of files to be created (number of inodes to be allocated) in the file system
     *
     * @param files Number of files to be formated
     * @return a boolean variable that indicate the successful of format
     */
    public boolean format(int files)
    {

        superblock.format(files);
        // Create a new instance of Directory and FileTable
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);
        return true;
    }

    /**
     * // - returns the size in bytes of the file indicated by fd
     *
     * @param ftEnt the File Table Entry
     * @return an integer value representing the file size
     */
    public int fsize(FileTableEntry ftEnt)
    {
        if (ftEnt == null)
        {
            return -1;
        }
        synchronized (ftEnt)
        {
            return ftEnt.inode.length;
        }
    }
}


