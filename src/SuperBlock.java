public class SuperBlock
{
    public int totalBlocks;
    public int totalInodes;
    public int freeList;


    //Constructor that is passed number of blocks
//Superblock is created by Filesystem.	
    public SuperBlock(int numBlocks)
    {
        byte[] block0 = new byte[Disk.blockSize];
        SysLib.rawread(0, block0);    //block and byte array are passed in
        totalBlocks = SysLib.bytes2int(block0, 0);
        totalInodes = SysLib.bytes2int(block0, 4);
        freeList = SysLib.bytes2int(block0, 8);
        if (totalBlocks == numBlocks && totalInodes > 0 && freeList >= 2)
        {
            return;
        } else
        {
            totalBlocks = numBlocks;
            format(64);
        }
    }


    /**
     * THis method sync the data in the superblock to block 0 in the disk
     *
     */


    public void sync()
    {
        byte[] blockInfo = new byte[Disk.blockSize];
//Pass syslib.int2bytes the int, byte array and offset	
//Since each int is 4 bytes. My offset increments by 4. 	
        SysLib.int2bytes(totalBlocks, blockInfo, 0);
        SysLib.int2bytes(totalInodes, blockInfo, 4);
        SysLib.int2bytes(freeList, blockInfo, 8);
//SysLib.rawwrite is passed blocknumber and byte array
        SysLib.rawwrite(0, blockInfo);

    }

//----------------------------------------------------------------

    /**
     * This method takes in a number of block to format and format those block, starting from front
     *
     * @param numberOfBlock number of Block to Format
     */
    public void format(int numberOfBlock)
    {
        // Total Block to format

        totalInodes = numberOfBlock;
        // Make all variables default by writing empty iNode to the block
        for (short i = 0; i < totalInodes; ++i)
        {
            Inode tempNode = new Inode();
            tempNode.flag = 0;
            tempNode.toDisk(i);
        }
        // Where to jump
        freeList = 2 + (totalInodes / 16);
        // Write the index of the next available block to the current block
        for (int i = freeList; i < this.totalBlocks; i++)
        {
            byte[] tempData = new byte[Disk.blockSize];
            SysLib.int2bytes(i + 1, tempData, 0);
            SysLib.rawwrite(i, tempData);
        }
        // Update the super block
        this.sync();
    }

//----------------------------------------------------------------

    /**
     * //Function finds the next free block using the freelist integer
     * //Starts out by checking to make sure freelist is larger than index 0 but less than
     * //the number of blocks.
     *
     * @param none
     * @return integer that it the block number of the next free block
     */

    public int findFreeBlock()
    {
        // Get the current free block
        int freeBlockNum = freeList;
        if (freeList > 0)
        {
            // Check if it is still within the range
            if (freeList < totalBlocks)
            {
                byte[] blockInfo = new byte[Disk.blockSize];
                SysLib.rawread(freeList, blockInfo);

                // Get the next free block
                freeList = SysLib.bytes2int(blockInfo, 0);

                SysLib.int2bytes(0, blockInfo, 0);
                // Update the current block that it now do not have any free block
                SysLib.rawwrite(freeBlockNum, blockInfo);

            }
        }

        return freeBlockNum;
    }

    /**
     * This method add the new free block back to the list of free block
     * It does this by setting the block in the parameter to be the free block
     * And setting the old free block to be this current next free block
     *
     * @param blockNumber the block number of the nre free block
     */
    public void addFreeBlock(int blockNumber)
    {
        // Invalid block number
        if (blockNumber < 0)
        {
            return;
        } else
        {
            byte[] tempData = new byte[Disk.blockSize];


            SysLib.int2bytes(freeList, tempData, 0);
            SysLib.rawwrite(blockNumber, tempData);
            freeList = blockNumber;

        }
        return;
    }
}