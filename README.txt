YOGA DHYAN WEBSITE - chalane ka tarika (Java 17 ya naya chahiye)

1. Poora folder (yoga-site) unzip karo. Andar yeh sab ek hi jagah rehna chahiye:
   Server.java, index.html, bg.jpg, bg.mp4, songs/, photos/, videos/
2. Terminal kholo aur folder me jao, jaise:  cd ~/Downloads/yoga-site
3. Compile karo:   javac Server.java
4. Server chalao:  java Server      ("Chalu hai: http://localhost:8080" likha aayega; Terminal band mat karna)
5. Browser me kholo:  http://localhost:8080     (index.html par double-click mat karo)

Pehla page Login hai. Neeche chhote link: "Create your account" aur "Forgot your password?".
Login karne walon ka Naam + Gmail/Phone: users.csv (Excel me khulta hai). Password accounts.txt me hash ke roop me.
Photos: photos/ folder me daalo, videos: videos/ folder me daalo. Page refresh par gallery me dikhenge.
Settings: sound band, volume, Om, brightness, language (Hindi/English/Punjabi/Bengali), paani ka alarm.
