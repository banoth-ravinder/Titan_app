#include <RFduinoBLE.h>
#include "MedianFilter.h"

#define DEVICE_NAME "RFduino HR"
#define SERVICE_UUID "00002220-0000-1000-8000-00805f9b34fb"

//
// Pin setup
//
const int analogPin = 3;
int val = 0;

//
// Filter setup
//
MedianFilter medianFilter = MedianFilter(5);

//
// Function Defs
//
void bleSendString(String sendstring);

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600); 
  
  RFduinoBLE.deviceName = DEVICE_NAME;
  RFduinoBLE.customUUID = SERVICE_UUID;
  RFduinoBLE.begin();  
}

void loop() {
  // sample every 50 milliseconds
  RFduino_ULPDelay( 50 );
  
  // read the input pin
  val = analogRead(analogPin);
  
  //
  // Apply median filter
  //
  medianFilter.addData(val);
  int median = (int)medianFilter.getMedian();
  Serial.print("Median: ");
  Serial.println(median);
  
  // send the data to bluetooth
  RFduinoBLE.sendInt(median);
}

void RFduinoBLE_onReceive(char *data, int len)
{
  // Display debug message
  Serial.println("Bar");
}
