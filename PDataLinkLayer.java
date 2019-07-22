import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Arrays;


/**
 * A data link layer that uses parity to perform error management.
 */
public class PDataLinkLayer extends DataLinkLayer {
    
    private byte currentSendingFrameNumber = (byte) 0b00000000;
    private byte currentReceivingFrameNumber = (byte) 0b00000000;
    private boolean isOkay = false;

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed. COULD BE 1 to 8 BYTES.
     * @return A complete frame.
     */
    protected byte[] createFrame (byte[] data) {
	Queue<Byte> framingData = new LinkedList<Byte>();
	
	framingData.add(startTag);
	
	
	byte meta = (byte) 0b00000000; // LSB #2 is always 0 bc this is DATA
	meta = (byte) (meta | currentSendingFrameNumber); // OR metadata byte LSB #1 with the current sending frame #

	
	
	// parity byte calculation below:
	int x = 0;
	int numOnes = 0;
	byte currentByte = meta;
	
	for(int j=0; j<8; j++){ // first the metadata byte
	    numOnes += (currentByte >>> j) & 1;
	}
	while(x < data.length){
	    currentByte = data[x];
	    for(int j=0; j<8; j++){ // then every other data byte
		numOnes += (currentByte >>> j) & 1;
	    }
	    x++;
	}
	byte parityByte = (byte)(numOnes % 2);
	framingData.add(parityByte); // append parity byte
	
	framingData.add(meta); // append metadata byte
	
	
	x = 0;
	while(x < data.length){
	    currentByte = data[x];
	    
	    if ((currentByte == startTag) || (currentByte == stopTag) || (currentByte == escapeTag)) {
		framingData.add(escapeTag);
	    }
	    framingData.add(currentByte); // add actual data byte TO QUEUE
	    
	    x++;
	}
	framingData.add(stopTag);
	
	
	
	// Convert queue to the byte array.
	byte[] framedData = new byte[framingData.size()];
	Iterator<Byte> i = framingData.iterator();
	int j = 0;
	while (i.hasNext()) {
	    framedData[j++] = i.next();
	}
	
	return framedData;
    }
    
    
    
    /**
     * Determine whether the received, buffered data constitutes a complete
     * frame.  If so, then remove the framing metadata and return the original
     * data.  Note that any data preceding an escaped start tag is assumed to be
     * part of a damaged frame, and is thus discarded.
     *
     * @return If the buffer contains a complete frame, the extracted, original
     * data; <code>null</code> otherwise.
     */
    protected Queue<Byte> processFrame () {
	
// Search for a start tag.  Discard anything prior to it.
	boolean startTagFound = false;
	
	Iterator<Byte> i = receiveBuffer.iterator();
	while (!startTagFound && i.hasNext()) {
	    byte current = i.next();
	    if (current != startTag) {
		i.remove();
	    } else {
		startTagFound = true;
	    }
	}
	
	
	// If there is no start tag, then there is no frame.
	if (!startTagFound) {
	    return null;
	}
	
	// Try to extract data while waiting for an unescaped stop tag.
	Queue<Byte> extractedBytes = new LinkedList<Byte>();
	
	boolean stopTagFound = false;
	
	while (!stopTagFound && i.hasNext()) {
	    byte current = i.next();
	    
	    if (current == escapeTag) {
		if (i.hasNext()) {
		    current = i.next();
		    extractedBytes.add(current);
		}
		else {
		    return null; // An escape was the last byte available, so this is not a complete frame.
		}
	    }
	    else if (current == stopTag) {
		cleanBufferUpTo(i);
		stopTagFound = true; // end extraction
	    }
	    else if (current == startTag) {
		cleanBufferUpTo(i); // remove preceding nonsense
		extractedBytes = new LinkedList<Byte>();
	    }
	    else {
		extractedBytes.add(current);
	    }
	}
	
	
	// If there is no stop tag, then the frame is incomplete.
	if (!stopTagFound) {
	    return null;
	}
	
	
	return extractedBytes; // return the queue without tags
    }
    
    
    

    








    protected void finishFrameReceive(Queue<Byte> data){
	
	int count = 0;
	int numOnes = 0;
	byte receivedParity = (byte)0;
	byte receivedMeta = (byte)0;
	
	
	
	// we convert Queue into byte array
	byte[] extractedData = new byte[data.size()-1]; // don't include the parity byte in extractedDtata array
	int m = -1;
	Iterator<Byte> iterate = data.iterator();
	while (iterate.hasNext()) {
	    if(m == -1){
		receivedParity = iterate.next();
		m++;
	    }
	    else{
		extractedData[m++] = iterate.next();
		if(m == 1){
		    receivedMeta = extractedData[m-1];
		}
    }
	}
	
	
	// we calculate the parity
	for(int i=0; i < extractedData.length; i++){
	    byte currentByte = extractedData[i];
	    for(int j=0; j<8; j++){
		numOnes += (currentByte >>> j) & 1;
	    }
	}
	
	byte calculatedParity = (byte)(numOnes%2);
	
	// we compare the calculated & received parities
	if(calculatedParity != receivedParity){ // parity mismatch
	    System.out.println("Parity does not match!");
	    System.out.print("Incorrect Sequence <");
	    for(int z=0 ; z < extractedData.length; z++){
		char letter = (char)extractedData[z];
		System.out.print(letter);
	    }
	    System.out.print(">");
	    System.out.println();

	    return; // just leave
}
	
	
	
	
	// we now know that parity is fine
	// so, we check if this byte array is an ack frame or data frame
	
	byte receivedFrameNumber = (byte)0; // from the metadata byte
	byte dataOrAck = (byte)0; // from the metadata byte
	
	receivedFrameNumber = (byte) (receivedFrameNumber | (receivedMeta & (byte)1));
	dataOrAck = (byte) (dataOrAck | ((receivedMeta >>> 1) & (byte)1));
	
	
	
// the main split: ack or data
	if(dataOrAck == (byte)0){ // based on metadata byte, it is data! (Host B getting data)
	    if(receivedFrameNumber != currentReceivingFrameNumber){
		byte[] ack = createAck(false); // RESEND for prev. frame
		transmit(ack);
	    }
	    else{
		byte[] ack = createAck(true); // send for current expected frame
		transmit(ack);
		client.receive(Arrays.copyOfRange(extractedData, 1, extractedData.length)); // don't send metadata byte to Host
		currentReceivingFrameNumber = (byte)(currentReceivingFrameNumber ^ (byte)0b00000001);
	    }
	}
	
	else if(dataOrAck == (byte)1){ // based on metadata byte, it is an ack! (Host A getting ack)
	    if(receivedFrameNumber == currentSendingFrameNumber){
		isOkay = true;
		System.out.println("We have received an ACK with correct frame number: " + receivedFrameNumber);
	    } // check the frame # is right
	}

    }
    
    
    protected void finishFrameSend(){
	if(isOkay){ // flip sending frame # after we break out of the while loop
	    currentSendingFrameNumber = (byte) (currentSendingFrameNumber ^ (byte)0b00000001);
	}
    }
    
    
    protected byte[] createAck(boolean prevOrCurr){ // just a start tag, PARITY byte, metadata byte, and stop tag
	byte metaByte = 0b00000010;
	if(prevOrCurr){ // use current receiving frame #
	    metaByte = (byte) (metaByte | currentReceivingFrameNumber);
	}
	else{ // use previous receiving frame #
	    metaByte = (byte) (metaByte | (currentReceivingFrameNumber ^ (byte)0b00000001));
	}
	
	int numOnes = 0;
	for(int j=0; j<8; j++){
	    numOnes += (metaByte >>> j) & 1;
	}
	byte parityByte = (byte)(numOnes % 2);
	
	byte[] ack = new byte[4];
	ack[0] = startTag;
	ack[1] = parityByte; // the parity byte
	ack[2] = metaByte; // the metadata byte
	ack[3] = stopTag;
	
	return ack;
    }
    
    
    
    // =========================================================================
    /**
     * Extract the next frame-worth of data from the sending buffer, frame it,
     * and then send it.
     */
    protected void sendNextFrame () { // NEW 
	
	// Extract a frame-worth of data from the sending buffer.
	int frameSize = ((sendBuffer.size() < MAX_FRAME_SIZE) ? sendBuffer.size() : MAX_FRAME_SIZE);
	byte[] data = new byte[frameSize];
	Iterator<Byte> i = sendBuffer.iterator();
    
    
	for (int j = 0; j < frameSize; j += 1) {
	    data[j] = i.next();
	    i.remove();
	}
	
	// Frame and transmit this chunk.
	byte[] framedData = createFrame(data);
	
	// isOkay starts here as FALSE
	int count = 0;
	while(!isOkay){
	    if(count == 0){
		System.out.println("We have sent frame " + currentSendingFrameNumber);
	    }
	    else{
		System.out.println("We have RESENT frame " + currentSendingFrameNumber);
	    }
	    count++;
	    
	    transmit(framedData);
	}
    finishFrameSend();
    isOkay = false; // reset boolean field isOkay to false
    
    } // sendNextFrame ()
    // =========================================================================
    
    
    
    
    
    
    
    
    private void cleanBufferUpTo (Iterator<Byte> end) { // unused
	Iterator<Byte> i = receiveBuffer.iterator();
	while (i.hasNext() && i != end) {
	    i.next();
	    i.remove();
	}
    }
    
    
    
    

    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    // ===============================================================
    
}
