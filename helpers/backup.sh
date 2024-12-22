#!/bin/bash

folders=( '/etc/' '/root/' '/opt/' '/var/' '/home/' )
excludes=( '/var/server_backups' )
target='/var/server_backups'

clear

rm -rf $target #altes Backup löschen
mkdir $target #Ordner für neues Backup erstellen

dt=$(date '+%Y%m%d-%H%M%S')
target="$target/$dt"

mkdir $target #Unterordner für neues Backup erstellen

echo "$(date '+%Y-%m-%d %H:%M:%S') | Starting backup"
echo "$(date '+%Y-%m-%d %H:%M:%S') | Starting backup" > $target/log.txt

echo "$(date '+%Y-%m-%d %H:%M:%S') | Create DB Dumps"
echo "$(date '+%Y-%m-%d %H:%M:%S') | Create DB Dumps" >> $target/log.txt
sudo gitlab-backup create

exclude="--exclude="
exclude+=$( echo $(IFS=, ; echo "${excludes[*]}") | sed 's/,/ --exclude=/g' )

for folder in "${folders[@]}"
do
   printf '\n%*s\n\n' "${COLUMNS:-$(tput cols)}" '' | tr ' ' -
   echo "$(date '+%Y-%m-%d %H:%M:%S') | Backup $folder"
   echo "$(date '+%Y-%m-%d %H:%M:%S') | Backup $folder" >> $target/log.txt
   tar cvf - $exclude $folder -P 2> "$target/$(echo $folder | tr / _).tar.gz.log" | pv -s $(du -sb $folder $exclude | awk '{print $1}') | gzip > "$target/$(echo $folder | tr / _).tar.gz"
done

echo "$(date '+%Y-%m-%d %H:%M:%S') | Finishing backup"
echo "$(date '+%Y-%m-%d %H:%M:%S') | Finishing backup" >> $target/log.txt
