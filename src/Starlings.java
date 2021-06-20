import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

public class Starlings extends PApplet {

	Flock flock;
	PeasyCam peasyCam = null;

	static final PVector ZERO = new PVector(0, 0, 0);
	static final PVector X = new PVector(1, 0, 0);
	static final PVector Y = new PVector(0, 1, 0);
	static final PVector Z = new PVector(0, 0, 1);

	static final int WORLD_WIDTH = 5000;
	static final int WORLD_HEIGHT = 5000;
	static final int WORLD_DEPTH = 5000;
	static final float WORLD_BORDER_FORCE_THRESHOLD = 0.8f;

	static final int SCREEN_WIDTH = 1500;
	static final int SCREEN_HEIGHT = 1000;

	static final float SEPARATION_FORCE_MULTIPLIER = 1.5f;
	static final float COHESION_FORCE_MULTIPLIER = 0.00f;
	static final float ALIGNMENT_FORCE_MULTIPLIER = 1f;

	static final float IDEAL_SEPARATION = 50.0f;

	static final float ALIGNMENT_NEIGHBOUR_DISTANCE = 100;
	static final float COHESION_NEIGHBOUR_DISTANCE = 400;
	static final float ATTRACTOR_NEIGHBOUR_DISTANCE = 50;

	static final float BOID_MAX_FORCE = 0.8f;
	static final float BOID_MAX_SPEED = 10;
	static final float BOID_FLOCK_SIZE = 1000;

	static PVector WORLD_CENTRE = null;
	static float WORLD_RADIUS;

	@Override
	public void settings() {
		size(SCREEN_WIDTH, SCREEN_HEIGHT, P3D);

		WORLD_CENTRE = new PVector(WORLD_WIDTH/2f,WORLD_HEIGHT/2f, WORLD_DEPTH/2f);
		WORLD_RADIUS = (WORLD_WIDTH + WORLD_HEIGHT + WORLD_DEPTH) / 6f;

		flock = new Flock();
		for (int i = 0; i < BOID_FLOCK_SIZE; i++) {
			flock.addBoid(new Boid(
				(int)(WORLD_WIDTH * Math.random()),
				(int)(WORLD_HEIGHT * Math.random()),
				(int)(WORLD_DEPTH * Math.random()))
			);
		}

		flock.addAttractor(new Attractor(new PVector(WORLD_WIDTH/4.0f,WORLD_HEIGHT/4.0f, WORLD_DEPTH/4.0f), ZERO, ZERO, Charge.NEGATIVE, 10));
	}

	@Override
	public void draw() {
		background(0);

		pushMatrix();
		noFill();
		stroke(100);
		translate(WORLD_WIDTH/2.0f, WORLD_HEIGHT/2.0f, WORLD_DEPTH/2.0f);
//		box(WORLD_WIDTH, WORLD_HEIGHT, WORLD_DEPTH);
		stroke(0, 0, 255, 50);
		sphere(WORLD_RADIUS);
		popMatrix();

		if (peasyCam == null) {
			peasyCam = new PeasyCam(this, getGraphics(), WORLD_WIDTH/2.0f, WORLD_HEIGHT/2.0f, WORLD_DEPTH/2.0f, WORLD_WIDTH);
			peasyCam.setMinimumDistance(100);
			peasyCam.setMaximumDistance(WORLD_WIDTH * 4);
		}
		flock.update();
	}

	public static void main(String[] args) {
		String[] appletArgs = new String[] { "Starlings" };
		Starlings starlings = new Starlings();
		PApplet.runSketch(appletArgs, starlings);
	}

	public static class Flock {
		List<Boid> boids = new ArrayList<>();
		List<Attractor> attractors = new ArrayList<>();
		float averageBoidSeparation;

		public Flock() {

		}

		public void addBoid(Boid boid) {
			boids.add(boid);
		}

		public void addAttractor(Attractor attractor) {
			attractors.add(attractor);
		}


		public void update() {
			float totalBoidSepatation = 0;
			for (Boid boid : boids) {
				boid.update(boids, attractors);
				boid.render();
				totalBoidSepatation += boid.averageSeparation;
			}

			averageBoidSeparation = totalBoidSepatation / boids.size();
//			System.out.println("averageBoidSeparation: " + averageBoidSeparation);

			for (Attractor attractor : attractors) {
				attractor.update();
				attractor.render();
			}
		}
	}

	public enum Charge {
		POSITIVE(0, 255, 0), NEGATIVE(255, 0, 0);

		public int red;
		public int green;
		public int blue;

		Charge(int red, int green, int blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
		}
	}

	public class Attractor {
		PVector position;
		PVector velocity;
		PVector acceleration;
		Charge charge;
		float attractorStrength;
		float r;

		public Attractor(
			PVector position,
			PVector velocity,
			PVector acceleration,
			Charge charge,
			float attractorStrength
			) {
			this.position = position;
			this.velocity = velocity;
			this.acceleration = acceleration;
			this.charge = charge;
			this.attractorStrength = attractorStrength;
			this.r = 2;
		}

		public void update() {

		}

		public void render() {
			fill(charge.red, charge.green, charge.blue, 100);
			noStroke();
			lights();
			translate(position.x, position.y, position.z);
			sphere(10);
		}
	}

	public class Boid {

		PVector position;
		PVector velocity;
		PVector acceleration;

		List<PVector> positionHistory = new ArrayList<>();

		float r; //??
		float maxForce;
		float maxSpeed;

		float averageSeparation;

		public Boid(int x, int y, int z) {

			acceleration = new PVector(0, 0, 0);

			velocity = PVector.random3D();

			position = new PVector(x, y, z);
			r = 2;
			maxForce = BOID_MAX_FORCE;
			maxSpeed = BOID_MAX_SPEED;
		}

		public void applyForce(PVector force) {
			acceleration.add(force);
		}

		public void update(List<Boid> boids, List<Attractor> attractors) {
			updateFlock(boids, attractors);
			updateKinematics();
		}

		public void updateFlock(List<Boid> boids, List<Attractor> attractors) {
			PVector separationForce = calcSeparationForce(boids);
			PVector alignmentForce = calcAlignmentForce(boids);
			PVector cohesionForce = calcCohesionForce(boids);
			PVector attractorForce = calcAttractorForce(attractors);
			PVector borderForce = calcBorderForce();

			separationForce.mult(SEPARATION_FORCE_MULTIPLIER);
			alignmentForce.mult(ALIGNMENT_FORCE_MULTIPLIER);
			cohesionForce.mult(COHESION_FORCE_MULTIPLIER);

			applyForce(borderForce);
			applyForce(separationForce);
			applyForce(alignmentForce);
			applyForce(cohesionForce);
			applyForce(attractorForce);

		}

		public PVector calcSeparationForce(List<Boid> boids) {
			PVector steer = new PVector(0, 0, 0);
			float totalSeparation = 0;
			int count = 0;
			for (Boid other : boids) {
				float distance = PVector.dist(position, other.position);
				totalSeparation += distance;

				if ((distance > 0) && (distance < IDEAL_SEPARATION)) {
					PVector diff = PVector.sub(position, other.position);
					diff.normalize();
					diff.div(distance);			// Weight by distance
					steer.add(diff);
					count++;
				}
			}

			averageSeparation = totalSeparation / boids.size();

			if (count > 0) {
				steer.div((float)count);
			}

			// As long as the vector is greater than 0
			if (steer.mag() > 0) {
				// Implement Reynolds: Steering = Desired - Velocity
				steer.setMag(maxSpeed);
				steer.sub(velocity);
				steer.limit(maxForce);
			}
			return steer;
		}

		public PVector calcAlignmentForce(List<Boid> boids) {
			PVector sum = new PVector(0, 0, 0);
			int count = 0;
			for (Boid other : boids) {
				float d = PVector.dist(position, other.position);
				if ((d > 0) && (d < ALIGNMENT_NEIGHBOUR_DISTANCE)) {
					sum.add(other.velocity);
					count++;
				}
			}
			if (count > 0) {
				sum.div((float)count);
				// Implement Reynolds: Steering = Desired - Velocity
				sum.setMag(maxSpeed);
				PVector steer = PVector.sub(sum, velocity);
				steer.limit(maxForce);
				return steer;
			}
			else {
				return new PVector(0, 0, 0);
			}
		}

		public PVector calcCohesionForce(List<Boid> boids) {
			PVector sum = new PVector(0, 0, 0);
			int count = 0;
			for (Boid other : boids) {
				float d = PVector.dist(position, other.position);
				if ((d > 0) && (d < COHESION_NEIGHBOUR_DISTANCE)) {
					sum.add(other.position);
					count++;
				}
			}
			if (count > 0) {
				sum.div(count);
				return seek(sum);  // Steer towards the position
			}
			else {
				return new PVector(0, 0, 0);
			}
		}

		public PVector calcAttractorForce(List<Attractor> attractors) {
			PVector attractorForce = ZERO.copy();
			for (Attractor attractor : attractors) {
				float d = PVector.dist(position, attractor.position);
				if ((d > 0) && (d < ATTRACTOR_NEIGHBOUR_DISTANCE)) {
					PVector force = attractor.position.copy().sub(position).normalize();
					force.div(radialFactor(d)).mult(attractor.attractorStrength);
					if (attractor.charge == Charge.NEGATIVE) {
						force.mult(-1);
					}

					attractorForce.add(force);
				}
			}

			return attractorForce;
		}

		public float radialFactor(float length) {
//			return (float) Math.pow(length, 0.5f);
			return length;
		}

		public PVector calcBorderForce() {

			//	General direction to centre of map
			PVector centreVector = WORLD_CENTRE.copy().sub(position);

			if (centreVector.mag() > WORLD_RADIUS * WORLD_BORDER_FORCE_THRESHOLD) {
				//	scaling desire to return towards centre by world radius
				centreVector.div((float)(WORLD_RADIUS * Math.PI));
				return centreVector;
			}

			return ZERO;
		}

		public void updateKinematics() {
			velocity.add(acceleration);
			velocity.limit(maxSpeed);

			position.add(velocity);

			acceleration.mult(0);

//			positionHistory.add(position.copy());
//			int maxPositions = 50;
//			if (positionHistory.size() > maxPositions) {
//				positionHistory = positionHistory.subList(positionHistory.size() - maxPositions, positionHistory.size() - 1);
//			}

		}

		public PVector seek(PVector target) {
			PVector desired = PVector.sub(target, position);  // A vector pointing from the position to the target
			 desired.setMag(maxSpeed);

			// Steering = Desired minus Velocity
			PVector steer = PVector.sub(desired, velocity);
			steer.limit(maxForce);  // Limit to maximum steering force
			return steer;
		}

//		public void stopCrossingBorders() {
//			float bufferDistance = 2f;
//			if (position.x < 0) position.x = bufferDistance;
//			if (position.y < 0) position.y = bufferDistance;
//			if (position.z < 0) position.z = bufferDistance;
//
//			if (position.x > WORLD_WIDTH) position.x = WORLD_WIDTH - bufferDistance;
//			if (position.y > WORLD_HEIGHT) position.y = WORLD_HEIGHT - bufferDistance;
//			if (position.z > WORLD_DEPTH) position.z = WORLD_DEPTH - bufferDistance;
//		}

		public void render() {
			fill(200, 100);
			stroke(255);
			drawCylinder(5, 0, 40, 5, position, velocity.copy());
			drawPositionHistory();
		}

		void drawPositionHistory() {
			for (PVector p : positionHistory) {
				stroke(200);
				point(p.x, p.y, p.z);
			}
		}
	}

	void drawCylinder(float bottom, float top, float h, int sides, PVector position, PVector direction)
	{
		pushMatrix();

		direction.normalize();

		translate(position.x, position.y, position.z);
		PVector rotationVector = direction.cross(Y);
		double rotationAngle = -Math.acos(Y.dot(direction));

//		int length = 100;
//		strokeWeight(5);
//
//		stroke(255, 50, 50, 50);
//		line(0, 0, 0, direction.x * length, direction.y * length, direction.z * length);
//
//		stroke(50, 255, 50, 50);
//		line(0, 0, 0, rotationVector.x * length, rotationVector.y * length, rotationVector.z * length);
//
//		PVector d = direction.cross(rotationVector);
//		stroke(50, 50, 255, 50);
//		line(0, 0, 0, d.x * length, d.y * length, d.z * length);
//
//		strokeWeight(1);

		if (rotationAngle > 0 || rotationAngle < 0) {
			rotate((float)rotationAngle, rotationVector.x, rotationVector.y, rotationVector.z);
		}

		stroke(255);
		float angle;
		float[] x = new float[sides+1];
		float[] z = new float[sides+1];

		float[] x2 = new float[sides+1];
		float[] z2 = new float[sides+1];

		//get the x and z position on a circle for all the sides
		for(int i=0; i < x.length; i++){
			angle = TWO_PI / (sides) * i;
			x[i] = sin(angle) * bottom;
			z[i] = cos(angle) * bottom;
		}

		for(int i=0; i < x.length; i++){
			angle = TWO_PI / (sides) * i;
			x2[i] = sin(angle) * top;
			z2[i] = cos(angle) * top;
		}

		//draw the bottom of the cylinder
		beginShape(TRIANGLE_FAN);

		vertex(0,   -h/2,    0);

		for(int i=0; i < x.length; i++){
			vertex(x[i], -h/2, z[i]);
		}

		endShape();

		//draw the center of the cylinder
		beginShape(QUAD_STRIP);

		for(int i=0; i < x.length; i++){
			vertex(x[i], -h/2, z[i]);
			vertex(x2[i], h/2, z2[i]);
		}

		endShape();

		//draw the top of the cylinder
		beginShape(TRIANGLE_FAN);

		vertex(0,   h/2,    0);

		for(int i=0; i < x.length; i++){
			vertex(x2[i], h/2, z2[i]);
		}

		endShape();


		popMatrix();
	}

}
