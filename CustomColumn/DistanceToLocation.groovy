String API_KEY="PUT_YOUR_API_KEY_HERE"

String projectLocation=null
def quoteOrProject = unwrappedThis.getProject()

if (quoteOrProject == null) {
  quoteOrProject = unwrappedThis.getTask().getQuote()
  projectLocation=quoteOrProject.getCustomFields().getTextField1()
} else {
  projectLocation=quoteOrProject.getCustomFields().getTextField3()
}


String vendorLocation;
if (unwrappedThis.getProvider()) {
 def address = unwrappedThis.getProvider().getAddress()
 StringBuffer vl = new StringBuffer()
 vl.append(address.getCountry().name)
 vl.append(" ").append(address.getCity())
 vl.append(" ").append(address.getAddress())
 if (address.getAddress2()) {
    vl.append(" ").append(address.getAddress2())
 }
 vendorLocation = vl.toString().replaceAll(" ", "+")
}

if (vendorLocation==null || vendorLocation=="") {
  return "Vendor missing for the job"
}

if (projectLocation==null || projectLocation=="") {
  return "Job location missing in project"
}



GMapHelper gm = new GMapHelper(API_KEY, vendorLocation, projectLocation)
def result = gm.getGMapData()

if (result.isValid()) {
  return "Distance:  <b>" +  result.distance() + "</b><br> Duration: <b> " +  result.duration()  + " </b><br><a target='_blank' href='"+ result.detailsMapURL() + "'><i>details</i></a>"  
} else {
  return result.errorMessage()  + result.toString()
}






class GMapHelper {     
    private static final String gMapAPIBaseURL="https://maps.googleapis.com/maps/api/distancematrix/json"
    private static final String gMapWebBaseURL="https://www.google.com/maps/dir/"

    String destinationAddress;
    String originAddress;
    String apiKey;
    String mode="driving"
    GmapResult gmapResult; 

    GMapHelper(apiKey, originAddress, destinationAddress){
       this.destinationAddress = destinationAddress;  
       this.originAddress = originAddress;  
       this.apiKey = apiKey;
    }

    GmapResult getGMapData(){
      if (!isLocationFine(destinationAddress)) {
         return GmapResult.incorrectLocation("destinationAddress")
      }
      if (!isLocationFine(originAddress)) {
         return GmapResult.incorrectLocation("originAddress")
      }


    
         String urlSuffix = "?origins=" + escapeAddress(originAddress) + 
               "&destinations=" + escapeAddress(destinationAddress) + "&mode="+mode+ "&key=" + apiKey

         String url = gMapAPIBaseURL + urlSuffix;
         String gMapResult = new URL(url).getText();
         def jsonSlurper = new groovy.json.JsonSlurper()
         def gMapResultObject = jsonSlurper.parseText(gMapResult)
         return new GmapResult(gMapResultObject)
    

    }
 
    private boolean isLocationFine(location) {
        if (location==null || location.length() <5) {
          return false;
        } else {
          return true;
        }
    }



    private String escapeAddress(String address){
      if (address) {
         address = address.replaceAll(" ", "+").replaceAll(",", "+")
      }
      return address
    }


    class GmapResult{
         private final OK_STATUS="OK"
         def details
         String status;
         String errorMessage
         GmapResult(details) {
            this(details.status, details.error_message, details)
         }

        private GmapResult(status, errorMessage, details) {
            this.errorMessage = errorMessage;
            this.status = status;
            this.details=details;

            if (details!=null) {
              int destinationAddressesSize = details.destination_addresses[0].size()
              int originAddressesSize = details.origin_addresses[0].size()
    
               if (destinationAddressesSize <1){
                 this.status = "DESTINATION_NOT_FOUND"
                 this.errorMessage = "Destination address not found by Google"
               } 
               if (originAddressesSize <1) {
                 this.status = "ORIGIN_NOT_FOUND"
                 this.errorMessage = "Origin address not found by Google"
              }
            }
         }

         static incorrectLocation(name){
            new GmapResult('BAD_PARAM', "Incorrect location param: " + name, null)
         }

         boolean isValid(){
            return status == OK_STATUS
         }

         String distance(){
           if (isValid()) {
             return details.rows.elements.findAll{item->item.distance!=null}[0][0].distance.text
           }  else {
             return errorMessage()
           }
         }

         String duration(){
           if (isValid()) {
             return details.rows.elements.findAll{item->item.duration!=null}[0][0].duration.text
           } else {
             return errorMessage()
           }
         }

         

        String detailsMapURL(){
            def destinationAddress = this.escapeAddress(details.destination_addresses[0])
            def originAddress = this.escapeAddress(details.origin_addresses[0])
            return gMapWebBaseURL + originAddress + "/" + destinationAddress
        }

         String errorMessage(){
            return errorMessage
         }

         String toString(){
           return details.toString()
         }

    }
}
