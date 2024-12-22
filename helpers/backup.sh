#!/bin/bash

folders=( '/etc/' '/root/' '/opt/' '/var/' '/home/' )
excludes=( '/var/server_backups' )
target='/var/server_backups'

clear

seven_days=$(date -d '7 days ago' '+%s')
stat -c "%Y %n" $target/*/ | sort -nr | tail -n +7 | while read -r mtime name; do
    if (( mtime < seven_days )); then
        echo "remove directory $name"
        rm -rf $name #altes Backup löschen
    fi
done
mkdir -p $target #Ordner für neues Backup erstellen

dt=$(date '+%Y%m%d-%H%M%S')
target="$target/$dt"

mkdir $target #Unterordner für neues Backup erstellen

echo "$(date '+%Y-%m-%d %H:%M:%S') | Starting backup"
echo "$(date '+%Y-%m-%d %H:%M:%S') | Starting backup" > $target/log.txt

sleep 2

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
