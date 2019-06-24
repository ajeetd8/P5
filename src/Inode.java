public class Inode
{
    private final static int iNodeSize = 32;
    private final static int directSize = 11;

    public int length;
    public short count;
    public short flag;
    public short direct[] = new short[directSize];
    public short indirect;
//-----------------------------------------------------------------
//Constructor methods below
//First constructor function creates a default inode that intializes 
//indirect and direct pointers to -1. 

    public Inode()
    {
        // Set everything to default value
        length = 0;
        count = 0;
        flag = 1;
        for (int i = 0; i < directSize; i++)
        {
            direct[i] = -1;
        }
        indirect = -1;
    }

    //2nd constructor function takes in inumber and creates an inode
//by pulling information from disk.

    public Inode(short iNumber)
    {
        int blockNumber = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);
        int offset = (iNumber % 16) * 32;
        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++)
        {
             short directBlock = SysLib.bytes2short(data, offset);
            direct[i] = directBlock;
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }


//-------------------------------------------------------------------

    /**
     * This method look for the block that the seek pointer is currently pointing too
     *
     * @param locationSeek The location of the pointer
     * @return an integer represent the location of the block
     */
    public int getBlockNumPointer(int locationSeek)
    {
        int offset = locationSeek / Disk.blockSize;
        // Still in the direct block of the iNode
        if (offset < 11)
        {
            return direct[offset];
        } else if (indirect == -1)
        {
            return -1;
        } else
        {
            byte[] tempData = new byte[Disk.blockSize];
            // Get the number of blocks that the indirect block is pointing to
            SysLib.rawread(indirect, tempData);
            // How far we are going to ?
            int difference = offset - 11;
            // Since difference is in int, we need to transfer it back to bytes by multiplying 2
            return SysLib.bytes2short(tempData, difference * 2);
        }
    }

    /**
     * This method update the block at the given position, given the pointer and the block number
     * @param position The location that is pointed to by the seek pointer
     * @param blockValue the block number
     * @return integer variable that indicate different status when we update the block
     */
    public int updateTheBlock(int position, short blockValue)
    {
        // How far into the block that we got into
        int directPointerIndex = position / Disk.blockSize;
        if (directPointerIndex < 11)
        {
            if (this.direct[directPointerIndex] >= 0)
            {
                // At this position, there is already a block in it
                return -1;
            } else if (directPointerIndex > 0 && this.direct[directPointerIndex - 1] == -1)
            {
                // The previous position is invalid
                return -2;
            } else
            {
                // Set the block number to the current location in the direct pointer,
                // And indicate successful
                direct[directPointerIndex] = blockValue;
                return 0;
            }
        }
        // If we are way out in the indirect location

        else if (this.indirect < 0)
        {
            // Nope, the indirect is not available
            return -3;
        }
        else
        {
            // We have an indirect location
            byte[] tempData = new byte[Disk.blockSize];
            // Read the data of the block in the indirect pointer
            SysLib.rawread(indirect, tempData);
            // How far into it should we go
            int offset = directPointerIndex - 11;
            if (SysLib.bytes2short(tempData, offset * 2) > 0)
            {

                return -1;
            }
            else
            {
                // Write the block number into the block at the indirect block
                SysLib.short2bytes(blockValue, tempData, offset * 2);
                SysLib.rawwrite(indirect, tempData);
                return 0;
            }
        }
    }

    /**s
     * Save to disk as the ith iNode
     * @param iNumber the inode number
     */
    //-------------------------------------------------------------------
//Moves from memory into disk using the inumber value
    public void toDisk(short iNumber)
    {
        if (iNumber < 0)
        {
            return;
        }
        // An inode only have 32 byte of info
        byte[] blockInfo = new byte[32];
        byte offset = 0;
        // Write the length, starting at 0
        SysLib.int2bytes(length, blockInfo, offset);
        int offsetForInt = offset + 4;
        // Write the count
        SysLib.short2bytes(count, blockInfo, offsetForInt);

        offsetForInt += 2;
        // Write the flag
        SysLib.short2bytes(flag, blockInfo, offsetForInt);
        offsetForInt += 2;

        // Now write back the block number that the direct pointer point to
        int pointerIndex;
        for (pointerIndex = 0; pointerIndex < directSize; pointerIndex++)
        {
            SysLib.short2bytes(direct[pointerIndex], blockInfo, offsetForInt);
            offsetForInt += 2;
        }
        // Write back the block number for the indirect pointer
        SysLib.short2bytes(this.indirect, blockInfo, offsetForInt);


        // Which Inode are we at given there are 16 inodes in 1 block
        pointerIndex = 1 + iNumber / 16;
        byte[] tempData = new byte[Disk.blockSize];
        SysLib.rawread(pointerIndex, tempData);
        offsetForInt = iNumber % 16 * iNodeSize;
        // Now write back the data into the disk given the location
        System.arraycopy(blockInfo, 0, tempData, offsetForInt, iNodeSize);
        SysLib.rawwrite(pointerIndex, tempData);
    }

    /**
     * This method take in the block number and update it by writing it back to the disk
     * @param blockValue the block number that we need to update for the indirect variable
     * @return a boolean variable indicating if we succeed or not
     */
    public boolean updateTheFreeBlock(short blockValue)
    {
        for (int i = 0; i < directSize; i++)
        {
            // If one of the direct block is invalid then, we should not change it

            if (this.direct[i] == -1)
            {
                return false;
            }
        }
        // The indirect block is already in use already, do not need to update
        if (indirect != -1)
        {
            return false;
        } else
        {
            // update it and get the data from the passed in paramter
            indirect = blockValue;
            byte[] tempData = new byte[Disk.blockSize];

            // Get the default data into the new direct block number by setting
            // everything to default which is -1
            for (int positionToWrite = 0; positionToWrite < Disk.blockSize/2; ++positionToWrite)
            {
                SysLib.short2bytes((short)-1, tempData, positionToWrite * 2);
            }
            // Write it to the disk
            SysLib.rawwrite(blockValue, tempData);
            return true;
        }



    }
}
