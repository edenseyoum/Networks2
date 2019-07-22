// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================



public class ParityDataLinkLayer extends DataLinkLayer {
// =============================================================================



    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */



    byte expectedFrNo = (byte) 0;
    byte ackExpectedFrNo = (byte) 0;
    byte FrameNumber = (byte) 0;
    boolean sent = false;

 
    protected byte[] createFrame (byte[] data) {

	//'intermediate' queue to call the calculateParity method only. 
	Queue<Byte> intermediate = new LinkedList<Byte>();
	intermediate.add(dataTag);
	intermediate.add(FrameNumber);
	for(int i =0; i < data.length; i +=1){
	    byte current = data[i];
	    intermediate.add(current);
	}
	byte parity = calculateParity(intermediate);




	Queue<Byte> framingData = new LinkedList<Byte>();
	 
	framingData.add(startTag);	

	framingData.add(parity);

	framingData.add(dataTag);

	framingData.add(FrameNumber);

	// Add each byte of original data.
	for (int i = 0; i < data.length; i += 1) {

	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
	    byte currentByte = data[i];
	    if ((currentByte == startTag) ||
		(currentByte == stopTag) ||
		(currentByte == escapeTag)) {

		framingData.add(escapeTag);

	    }

	    // Add the data byte itself.
	    framingData.add(currentByte);

	}

	// End with a stop tag.
	framingData.add(stopTag);

	// Convert to the desired byte array.
	byte[] framedData = new byte[framingData.size()];
	Iterator<Byte>  i = framingData.iterator();
	int             j = 0;
	while (i.hasNext()) {
	    framedData[j++] = i.next();
	}

	return framedData;
	
    } // createFrame ()
    // =======================================================================



    //boolean sent = false;
    
    //@Override
    protected void sendNextFrame () {
	
	// Extract a frame-worth of data from the sending buffer.
	int frameSize = ((sendBuffer.size() < MAX_FRAME_SIZE)
			 ? sendBuffer.size()
			 : MAX_FRAME_SIZE);
	byte[]         data = new byte[frameSize];
	Iterator<Byte>    i = sendBuffer.iterator();
	for (int j = 0; j < frameSize; j += 1) {
	    data[j] = i.next();
	    i.remove();
	}
	
	// Frame and transmit this chunk.
	byte[] framedData = createFrame(data);

	while(sent == false){
	    transmit(framedData);
	    if(FrameNumber == (byte) 0){
		FrameNumber = (byte) 1;
	    }
	    else if(FrameNumber == (byte) 1){
		FrameNumber = (byte) 0;
	    }
	    //sendAgain = true;
	}
	
	
	// Finish any bookkeeping with respect to this frame having been sent.
	finishFrameSend();
	sent = false;
	
	} // sendNextFrame ()
    



    // =========================================================================


    public byte calculateParity(Queue<Byte> data){
	//boolean evenOnes;
	Queue<Byte> copy = data;
	Iterator<Byte>    i = copy.iterator();
	int k = 0;
	while(i.hasNext()){
	    byte b = i.next();
	    for(int j=0; j< copy.size(); j++){
		k = k + ((b >>> j) & 1);
	    }
	}
	byte parity;
	if ((k % 2) == 0){
	    parity = (byte) 0;
	    return parity;
	    //evenOnes = true;
	}
	else{
	    parity=(byte) 1;
	    return parity;
	}
	
	
    }//calculateParity


    //===========================================================================

    //change the FrameNumber if the data is sent and an ack is received ('sendAgain' is false when an ack is received.
    protected void finishFrameSend (){
	if(sent == true){
	    if(FrameNumber == (byte) 0){
		FrameNumber = (byte) 1;
	    }
	    else if(FrameNumber == (byte) 1){
		FrameNumber = (byte) 0;
	    }
	}
    }





    //============================================================================

    //byte expectedFrNo = (byte) 0;
    //byte ackExpectedFrNo = (byte) 0; 

    protected void finishFrameReceive (Queue<Byte> data){


	//saving the frameNO byte, the dataTag and the parity first
	Queue<Byte> copy = data;
	Iterator<Byte> i = copy.iterator();
	byte parityFound = i.next();
	//System.out.println(parityFound);
	i.remove();
	byte dataTagFound = i.next();
	byte FrameNoFound = i.next();


	//if it is data, saving just the data in 'justData' queue
	Queue<Byte> justData = new LinkedList<Byte>();
	if(dataTagFound == (byte) 1){
	    while(i.hasNext()){
		justData.add(i.next());
	    }
	}
	
	//if it is not data, saving the Ack or Nak byte in 'AckOrNak'
	if(dataTagFound == (byte) 0){
	       while(i.hasNext()){
		byte AckOrNak = i.next();
	    }
	}

	//copying 'justData' queue into an array, 'justDataArray' 
	Iterator<Byte> j = justData.iterator();
	byte[] justDataArray = new byte[justData.size()];
	for(int k=0; k < justDataArray.length; k++){
	    justDataArray[k] = j.next();
	}
	
	
	//calculating the parity of the data (the parity byte has been removed from 'copy')
	byte checkParity = calculateParity(copy);
	
	//if parity doesn't match send nak (sendNonData method calls transmit)
	if(checkParity != parityFound){
	    Queue<Byte> nak = createAckNak(false, FrameNoFound);
		sendNonData(nak);
		//System.out.println("parity doesn't match for frame " + FrameNoFound);
	}


	else if (checkParity == parityFound){
	    
	    //if the parity matches, it is a data Frame and the expectedFrameNo mathes the FrameNoFound, send Ack, change the value of expectedFrNo and send the data.
	    if(dataTagFound == (byte) 1 && FrameNoFound == expectedFrNo){ 
		Queue<Byte> ack = createAckNak(false, FrameNoFound);
		sendNonData(ack);
		if(expectedFrNo == (byte) 0){
		    expectedFrNo = (byte) 1;
		}
		else if(expectedFrNo == (byte) 1){
		    expectedFrNo = (byte) 0;
		}
		//expectedFrNo = expectedFrNo ^ (byte) 1;
		client.receive(justDataArray);
		System.out.println("frame all good. sent frame " + FrameNoFound);
	    }
	    
	    //if the parity matches, it is a data frame but the expectedFrameNo does not match the FrameNoFound, 
	    else if(dataTagFound == (byte) 1 && FrameNoFound != expectedFrNo){
		if(FrameNoFound == 0){
		    Queue<Byte> resentAck = createAckNak(true, (byte) 1);
		}
		else if(FrameNoFound == 1){
		    Queue<Byte> resentAck = createAckNak(true, (byte) 0);
		}
	    }
	    
	    
	    //if the parity matches, the frame is NOT data and the frameNo matches the expected ack frame number, change sendAgain ot false and change expectedFrNo
	    else if(dataTagFound == (byte) 0 && FrameNoFound == ackExpectedFrNo){
		expectedFrNo = (byte) 1;
		sent = true;
	    }
	    
	}
	
    }//finishFrameReceive
    
    
    //==========================================================
    

    private Queue<Byte> createAckNak(boolean isAck, byte frameNo){
	Queue<Byte> AckOrNak = new LinkedList<Byte>();
	AckOrNak.add(startTag);

	//'intermediate' queue is used to calculateParity first
	Queue<Byte> intermediate = new LinkedList<Byte>();
	intermediate.add(NotData);
	intermediate.add(frameNo);
	if(isAck){
	    intermediate.add(Ack);}
	else{
	    intermediate.add(Nak);}

	
	byte parity = calculateParity(intermediate);


	AckOrNak.add(parity);
	AckOrNak.add(NotData);
	AckOrNak.add(frameNo);

	if(isAck == true){
	    AckOrNak.add(Ack);
	}
    
	else if(isAck != false){
	    AckOrNak.add(Nak);
	}

	AckOrNak.add(stopTag);

	return AckOrNak;

    }//createAckNak

    //=========================================================



    private void sendNonData(Queue<Byte> Data){
	Queue<Byte> nonDataFr = Data;
	byte[] nonData = new byte[nonDataFr.size()];
	Iterator<Byte> i = nonDataFr.iterator();
	for(int j=0; j< nonData.length; j+=1){
	    nonData[j] = i.next();
	}
	transmit(nonData);
    }//sendNonData
	

		
		
		
		    

		//====================================================

    protected Queue<Byte> processFrame () {

	// Search for a start tag.  Discard anything prior to it.
	boolean        startTagFound = false;
	Iterator<Byte>             i = receiveBuffer.iterator();
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
	boolean       stopTagFound = false;
	while (!stopTagFound && i.hasNext()) {

	    // Grab the next byte.  If it is...
	    //   (a) An escape tag: Skip over it and grab what follows as
	    //                      literal data.
	    //   (b) A stop tag:    Remove all processed bytes from the buffer and
	    //                      end extraction.
	    //   (c) A start tag:   All that precedes is damaged, so remove it
	    //                      from the buffer and restart extraction.
	    //   (d) Otherwise:     Take it as literal data.
	    byte current = i.next();
	    if (current == escapeTag) {
		if (i.hasNext()) {
		    current = i.next();
		    extractedBytes.add(current);
		} else {
		    // An escape was the last byte available, so this is not a
		    // complete frame.
		    return null;
		}
	    } else if (current == stopTag) {
		cleanBufferUpTo(i);
		stopTagFound = true;
	    } else if (current == startTag) {
		cleanBufferUpTo(i);
		extractedBytes = new LinkedList<Byte>();
	    } else {
		extractedBytes.add(current);
	    }

	}

	// If there is no stop tag, then the frame is incomplete.
	if (!stopTagFound) {
	    return null;
	}

	// Convert to the desired byte array.
	if (debug) {
	    System.out.println("ParityLinkLayer.processFrame(): Got whole frame!");
	}
	return extractedBytes;



    } // processFrame ()
    // ===============================================================



    // ===============================================================
    private void cleanBufferUpTo (Iterator<Byte> end) {

	Iterator<Byte> i = receiveBuffer.iterator();
	while (i.hasNext() && i != end) {
	    i.next();
	    i.remove();
	}

    }
    // ===============================================================



    // ===============================================================
    // DATA MEMBERS
    // ===============================================================



    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    private final byte dataTag = (byte) 1;
    private final byte NotData = (byte) 0;
    private byte Ack = (byte) 1;
    private byte Nak = (byte) 0;
    private byte NonDataCounter = (byte) 0;
    private byte DataCounter = (byte) 0;
    


    
    
    // ===============================================================



// ===================================================================
} // class ParityDataLinkLayer
// ===================================================================
