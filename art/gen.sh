#!/bin/sh -e

c() {
	convert $1 -size $2 ../src/main/res/drawable-$3/$1
}

# TODO calc size
for n in *.png; do
	c $n 150x150 mdpi
	c $n 200x200 hdpi
	c $n 250x250 xhdpi
	c $n 400x400 xxhdpi
done
