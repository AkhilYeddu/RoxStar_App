const mongoose = require('mongoose');
const config = require('../../backend/src/config/config');
const User = require('../../backend/src/models/User');
const Room = require('../../backend/src/models/Room');
const RoomMember = require('../../backend/src/models/RoomMember');
const Draft = require('../../backend/src/models/Draft');

const seedData = async () => {
  try {
    await mongoose.connect(config.mongoUri);
    console.log('Connected to database for seeding...');

    // Clean existing records
    await Promise.all([
      User.deleteMany({}),
      Room.deleteMany({}),
      RoomMember.deleteMany({}),
      Draft.deleteMany({}),
    ]);

    // 1. Create Demo Users
    const users = await User.create([
      { userId: 'user_alex', username: 'Alex_Vocalist', virtualPoints: 350 },
      { userId: 'user_sarah', username: 'Sarah_SoundEng', virtualPoints: 500 },
      { userId: 'user_mike', username: 'Mike_Producer', virtualPoints: 200 },
      { userId: 'user_elena', username: 'Elena_Singer', virtualPoints: 150 },
    ]);
    console.log(`Seeded ${users.length} demo users.`);

    // 2. Create Demo Room
    const demoRoom = await Room.create({
      roomId: 'room_studio_alpha',
      name: 'RoxStar Audio Jam & Spin Arena',
      ownerId: 'user_alex',
      status: 'IDLE',
      maxParticipants: 20,
    });

    // 3. Add Members
    await RoomMember.create([
      { roomId: demoRoom.roomId, userId: 'user_alex', username: 'Alex_Vocalist', role: 'OWNER', connectionStatus: 'CONNECTED' },
      { roomId: demoRoom.roomId, userId: 'user_sarah', username: 'Sarah_SoundEng', role: 'MEMBER', connectionStatus: 'CONNECTED' },
      { roomId: demoRoom.roomId, userId: 'user_mike', username: 'Mike_Producer', role: 'MEMBER', connectionStatus: 'CONNECTED' },
      { roomId: demoRoom.roomId, userId: 'user_elena', username: 'Elena_Singer', role: 'MEMBER', connectionStatus: 'CONNECTED' },
    ]);
    console.log(`Seeded room '${demoRoom.name}' with 4 active connected members.`);

    // 4. Create Sample Drafts
    await Draft.create([
      {
        draftId: 'draft_demo_01',
        userId: 'user_alex',
        title: 'Lead Vocal Harmony Hook',
        durationMs: 4200,
        effectApplied: 'ECHO',
        sharedInRooms: [demoRoom.roomId],
      },
      {
        draftId: 'draft_demo_02',
        userId: 'user_sarah',
        title: 'Acoustic Ambience Reverb Clip',
        durationMs: 7800,
        effectApplied: 'REVERB',
        sharedInRooms: [demoRoom.roomId],
      },
    ]);
    console.log('Seeded sample voice drafts.');

    console.log('✅ Seeding completed successfully!');
    process.exit(0);
  } catch (error) {
    console.error('Error seeding database:', error);
    process.exit(1);
  }
};

seedData();
